import unittest
from datetime import datetime, timezone
from decimal import Decimal
from uuid import UUID, uuid4

from sqlalchemy import create_engine, func, select
from sqlalchemy.orm import sessionmaker

from app import main
from app.models import Base, Bet, MatchCancellation, MatchSnapshot, OutboxEvent, ProcessedEvent


class CancellationSagaTest(unittest.TestCase):
    def setUp(self) -> None:
        self.engine = create_engine("sqlite+pysqlite:///:memory:")
        Base.metadata.create_all(self.engine)
        self.sessions = sessionmaker(bind=self.engine, expire_on_commit=False)
        main.SessionLocal = self.sessions

    def tearDown(self) -> None:
        self.engine.dispose()

    def create_bet(self, status: str) -> Bet:
        bet = Bet(id=str(uuid4()), user_id=str(uuid4()), match_id=str(uuid4()),
                  home_team_goals=2, away_team_goals=1, stake_amount=Decimal("10.00"),
                  status=status, created_at=datetime.now(timezone.utc), updated_at=datetime.now(timezone.utc),
                  idempotency_key=str(uuid4()))
        with self.sessions() as db:
            db.add(bet)
            db.commit()
        return bet

    def test_duplicate_match_event_generates_single_refund(self) -> None:
        bet = self.create_bet("CONFIRMED")
        event = {"event_id": str(uuid4()), "event_type": "MATCH_CANCELED",
                 "match_id": bet.match_id, "reason": "Cancelamento administrativo"}

        main.handle_event(main.settings.kafka_match_topic, event)
        main.handle_event(main.settings.kafka_match_topic, event)

        with self.sessions() as db:
            self.assertEqual("REFUND_PENDING", db.get(Bet, bet.id).status)
            self.assertEqual(1, db.scalar(select(func.count(OutboxEvent.id))))
            self.assertEqual(1, db.scalar(select(func.count(ProcessedEvent.id))))

    def test_match_events_build_enriched_projection(self) -> None:
        match_id = str(uuid4())
        created = {
            "event_id": str(uuid4()),
            "event_type": "MATCH_CREATED",
            "match_id": match_id,
            "team_home": "Aurora",
            "team_away": "Estrela",
            "scheduled_start": "2026-07-20T20:00:00Z",
            "score": {"team_home": 0, "team_away": 0},
        }
        main.handle_event(main.settings.kafka_match_topic, created)
        main.handle_event(main.settings.kafka_match_topic, {
            **created,
            "event_id": str(uuid4()),
            "event_type": "MATCH_STARTED",
        })
        main.handle_event(main.settings.kafka_match_topic, {
            **created,
            "event_id": str(uuid4()),
            "event_type": "TEAM_HOME_SCORED",
            "score": {"team_home": 1, "team_away": 0},
        })
        main.handle_event(main.settings.kafka_match_topic, {
            **created,
            "event_id": str(uuid4()),
            "event_type": "MATCH_ENDED",
            "score": {"team_home": 1, "team_away": 0},
        })

        with self.sessions() as db:
            snapshot = db.get(MatchSnapshot, match_id)
            self.assertEqual("Aurora", snapshot.team_home)
            self.assertEqual("Estrela", snapshot.team_away)
            self.assertEqual("FINISHED", snapshot.status)
            self.assertEqual(1, snapshot.home_team_goals)

    def test_payment_confirmed_after_cancellation_is_refunded_once(self) -> None:
        bet = self.create_bet("CREATED")
        main.handle_event(main.settings.kafka_match_topic,
                          {"event_id": str(uuid4()), "event_type": "MATCH_CANCELED",
                           "match_id": bet.match_id, "reason": "Cancelamento administrativo"})
        payment = {"eventId": str(uuid4()), "eventType": "PAYMENT_ACCEPTED", "betId": bet.id}
        main.handle_event(main.settings.kafka_wallet_topic, payment)
        main.handle_event(main.settings.kafka_wallet_topic, payment)

        with self.sessions() as db:
            self.assertEqual("REFUND_PENDING", db.get(Bet, bet.id).status)
            self.assertEqual(1, db.scalar(select(func.count(OutboxEvent.id))))

        main.handle_event(main.settings.kafka_wallet_topic,
                          {"eventId": str(uuid4()), "eventType": "PAYMENT_REFUNDED", "betId": bet.id})
        with self.sessions() as db:
            self.assertEqual("CANCELED", db.get(Bet, bet.id).status)

    def test_payment_refused_after_cancellation_does_not_request_refund(self) -> None:
        bet = self.create_bet("CREATED")
        main.handle_event(main.settings.kafka_match_topic,
                          {"event_id": str(uuid4()), "event_type": "MATCH_CANCELED",
                           "match_id": bet.match_id, "reason": "Cancelamento administrativo"})
        main.handle_event(main.settings.kafka_wallet_topic,
                          {"eventId": str(uuid4()), "eventType": "PAYMENT_REFUSED", "betId": bet.id})

        with self.sessions() as db:
            self.assertEqual("CANCELED", db.get(Bet, bet.id).status)
            self.assertEqual(0, db.scalar(select(func.count(OutboxEvent.id))))

    def test_payment_already_refused_is_resolved_without_refund(self) -> None:
        bet = self.create_bet("PAYMENT_REFUSED")
        main.handle_event(main.settings.kafka_match_topic,
                          {"event_id": str(uuid4()), "event_type": "MATCH_CANCELED",
                           "match_id": bet.match_id, "reason": "Cancelamento administrativo"})

        with self.sessions() as db:
            progress = main.build_refund_progress(UUID(bet.match_id), db)
            self.assertEqual("PAYMENT_REFUSED", db.get(Bet, bet.id).status)
            self.assertEqual("COMPLETED", progress.status)
            self.assertEqual(1, progress.completed)
            self.assertEqual(0, db.scalar(select(func.count(OutboxEvent.id))))

    def test_refund_failure_remains_visible_for_reprocessing(self) -> None:
        bet = self.create_bet("REFUND_PENDING")
        with self.sessions() as db:
            db.add(MatchCancellation(match_id=bet.match_id, reason="Cancelamento administrativo",
                                     canceled_at=datetime.now(timezone.utc)))
            db.commit()
        main.handle_event(main.settings.kafka_wallet_topic,
                          {"eventId": str(uuid4()), "eventType": "PAYMENT_REFUND_FAILED", "betId": bet.id})

        with self.sessions() as db:
            self.assertEqual("REFUND_FAILED", db.get(Bet, bet.id).status)
            progress = main.retry_failed_refunds(UUID(bet.match_id), {}, db)
            self.assertEqual("PROCESSING", progress.status)
            self.assertEqual("REFUND_PENDING", db.get(Bet, bet.id).status)
            self.assertEqual(1, db.scalar(select(func.count(OutboxEvent.id))))


if __name__ == "__main__":
    unittest.main()
