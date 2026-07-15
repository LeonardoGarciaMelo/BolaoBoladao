import json
import unittest
from datetime import datetime, timezone
from decimal import Decimal
from uuid import uuid4

from sqlalchemy import create_engine, func, select
from sqlalchemy.orm import sessionmaker

from app import main
from app.models import Base, Bet, MatchSnapshot, OutboxEvent


class BetSettlementTest(unittest.TestCase):
    def setUp(self) -> None:
        self.engine = create_engine("sqlite+pysqlite:///:memory:")
        Base.metadata.create_all(self.engine)
        self.sessions = sessionmaker(bind=self.engine, expire_on_commit=False)
        main.SessionLocal = self.sessions
        self.match_id = str(uuid4())
        with self.sessions() as db:
            db.add(MatchSnapshot(
                match_id=self.match_id,
                team_home="Aurora",
                team_away="Estrela",
                scheduled_start=datetime.now(timezone.utc),
                status="IN_PROGRESS",
                home_team_goals=0,
                away_team_goals=0,
                updated_at=datetime.now(timezone.utc),
            ))
            db.commit()

    def tearDown(self) -> None:
        self.engine.dispose()

    def add_bet(self, home: int, away: int, amount: str, status: str = "CONFIRMED") -> str:
        bet_id = str(uuid4())
        with self.sessions() as db:
            db.add(Bet(
                id=bet_id,
                user_id=str(uuid4()),
                match_id=self.match_id,
                home_team_goals=home,
                away_team_goals=away,
                stake_amount=Decimal(amount),
                status=status,
                created_at=datetime.now(timezone.utc),
                updated_at=datetime.now(timezone.utc),
                idempotency_key=str(uuid4()),
            ))
            db.commit()
        return bet_id

    def finish_match(self, home: int, away: int) -> None:
        main.handle_event(main.settings.kafka_match_topic, {
            "event_id": str(uuid4()),
            "event_type": "MATCH_ENDED",
            "match_id": self.match_id,
            "team_home": "Aurora",
            "team_away": "Estrela",
            "scheduled_start": datetime.now(timezone.utc).isoformat(),
            "score": {"team_home": home, "team_away": away},
        })

    def test_sem_placar_exato_todos_perdem_e_nenhum_premio_e_emitido(self) -> None:
        first = self.add_bet(1, 0, "10.00")
        second = self.add_bet(3, 1, "30.00")

        self.finish_match(2, 1)

        with self.sessions() as db:
            self.assertEqual("LOST", db.get(Bet, first).status)
            self.assertEqual("LOST", db.get(Bet, second).status)
            self.assertIsNone(db.get(Bet, first).won_amount)
            self.assertEqual(0, db.scalar(select(func.count(OutboxEvent.id))))

    def test_rateia_bolao_proporcionalmente_apenas_entre_acertos_exatos(self) -> None:
        winner_ten = self.add_bet(2, 1, "10.00")
        winner_thirty = self.add_bet(2, 1, "30.00")
        loser = self.add_bet(1, 0, "60.00")
        refused = self.add_bet(2, 1, "100.00", "PAYMENT_REFUSED")

        self.finish_match(2, 1)

        with self.sessions() as db:
            self.assertEqual(Decimal("25.00"), db.get(Bet, winner_ten).won_amount)
            self.assertEqual(Decimal("75.00"), db.get(Bet, winner_thirty).won_amount)
            self.assertEqual("LOST", db.get(Bet, loser).status)
            self.assertEqual("PAYMENT_REFUSED", db.get(Bet, refused).status)
            events = db.scalars(select(OutboxEvent).order_by(OutboxEvent.created_at)).all()
            self.assertEqual(2, len(events))
            self.assertEqual({"25.00", "75.00"}, {json.loads(event.payload)["amount"] for event in events})
            self.assertTrue(all(json.loads(event.payload)["eventType"] == "BET_SETTLED" for event in events))

    def test_vencedor_unico_recebe_somente_o_total_apostado_sem_bonus(self) -> None:
        winner = self.add_bet(2, 1, "10.00")

        self.finish_match(2, 1)

        with self.sessions() as db:
            settled = db.get(Bet, winner)
            self.assertEqual("WON", settled.status)
            self.assertEqual(Decimal("10.00"), settled.won_amount)
            event = db.scalar(select(OutboxEvent))
            self.assertEqual("10.00", json.loads(event.payload)["amount"])

    def test_rateio_em_centavos_nao_paga_acima_do_bolao(self) -> None:
        self.add_bet(2, 1, "1.00")
        self.add_bet(2, 1, "1.00")
        self.add_bet(1, 0, "1.03")

        self.finish_match(2, 1)

        with self.sessions() as db:
            prizes = sorted(db.scalars(
                select(Bet.won_amount).where(Bet.match_id == self.match_id, Bet.status == "WON")
            ).all())
            self.assertEqual([Decimal("1.51"), Decimal("1.52")], prizes)
            self.assertEqual(Decimal("3.03"), sum(prizes))

    def test_rateio_em_centavos_distribui_todo_o_residuo_do_bolao(self) -> None:
        self.add_bet(2, 1, "1.00")
        self.add_bet(2, 1, "1.00")
        self.add_bet(2, 1, "1.00")
        self.add_bet(1, 0, "1.00")

        self.finish_match(2, 1)

        with self.sessions() as db:
            prizes = sorted(db.scalars(
                select(Bet.won_amount).where(Bet.match_id == self.match_id, Bet.status == "WON")
            ).all())
            self.assertEqual([Decimal("1.33"), Decimal("1.33"), Decimal("1.34")], prizes)
            self.assertEqual(Decimal("4.00"), sum(prizes))


if __name__ == "__main__":
    unittest.main()
