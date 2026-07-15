import unittest
from datetime import datetime, timedelta, timezone
from uuid import uuid4

from fastapi import HTTPException
from pydantic import ValidationError
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool

from app import main
from app.models import Base, Bet, MatchSnapshot


class UserBetsApiTest(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self) -> None:
        self.engine = create_engine(
            "sqlite+pysqlite://",
            connect_args={"check_same_thread": False},
            poolclass=StaticPool,
        )
        Base.metadata.create_all(self.engine)
        self.sessions = sessionmaker(bind=self.engine, expire_on_commit=False)
        main.SessionLocal = self.sessions

        self.user_id = uuid4()
        self.match_id = uuid4()
        with self.sessions() as db:
            db.add(MatchSnapshot(
                match_id=str(self.match_id),
                team_home="Aurora",
                team_away="Estrela",
                scheduled_start=datetime.now(timezone.utc) + timedelta(hours=2),
                status="SCHEDULED",
                home_team_goals=0,
                away_team_goals=0,
                updated_at=datetime.now(timezone.utc),
            ))
            db.commit()

    async def asyncTearDown(self) -> None:
        self.engine.dispose()

    def headers(self, key: str) -> dict[str, str]:
        return {
            "X-Authenticated-User-Id": str(self.user_id),
            "Idempotency-Key": key,
        }

    def payload(self, home: int = 2) -> dict[str, object]:
        return {
            "match_id": str(self.match_id),
            "home_team_goals": home,
            "away_team_goals": 1,
            "stake_amount": "10.00",
        }

    async def test_idempotencia_distingue_retry_de_palpite_identico_deliberado(self) -> None:
        with self.sessions() as db:
            first = await main.create_bet(main.BetCreateRequest(**self.payload()), "first-key", self.user_id, db)
            retry = await main.create_bet(main.BetCreateRequest(**self.payload()), "first-key", self.user_id, db)
            repeated = await main.create_bet(main.BetCreateRequest(**self.payload()), "second-key", self.user_id, db)

            self.assertEqual(first.bet_id, retry.bet_id)
            self.assertNotEqual(first.bet_id, repeated.bet_id)

            with self.assertRaises(HTTPException) as conflict:
                await main.create_bet(main.BetCreateRequest(**self.payload(home=3)), "first-key", self.user_id, db)
            self.assertEqual(409, conflict.exception.status_code)

            page = main.list_bets(None, 0, 10, self.user_id, db)
            self.assertEqual(2, page.total)
            self.assertEqual("Aurora", page.items[0].match.team_home)

    async def test_rejeita_partida_ausente_encerrada_ou_valor_abaixo_do_minimo(self) -> None:
        with self.sessions() as db:
            missing = self.payload()
            missing["match_id"] = str(uuid4())
            with self.assertRaises(HTTPException) as unavailable:
                await main.create_bet(main.BetCreateRequest(**missing), "missing", self.user_id, db)
            self.assertEqual(409, unavailable.exception.status_code)

            too_small = self.payload()
            too_small["stake_amount"] = "0.99"
            with self.assertRaises(ValidationError):
                main.BetCreateRequest(**too_small)

            snapshot = db.get(MatchSnapshot, str(self.match_id))
            exact_start = datetime.now(timezone.utc)
            snapshot.scheduled_start = exact_start
            db.commit()
            original_now = main.now
            main.now = lambda: exact_start
            try:
                with self.assertRaises(HTTPException) as closed:
                    await main.create_bet(main.BetCreateRequest(**self.payload()), "closed", self.user_id, db)
                self.assertEqual(409, closed.exception.status_code)
            finally:
                main.now = original_now

    async def test_pagina_e_isola_consultas_por_usuario(self) -> None:
        other_user = uuid4()
        with self.sessions() as db:
            created = [
                await main.create_bet(main.BetCreateRequest(**self.payload(home=home)), f"key-{home}", self.user_id, db)
                for home in range(3)
            ]
            foreign = await main.create_bet(
                main.BetCreateRequest(**self.payload(home=5)), "foreign-key", other_user, db
            )

            first_page = main.list_bets(None, 0, 2, self.user_id, db)
            second_page = main.list_bets(None, 1, 2, self.user_id, db)

            self.assertEqual(3, first_page.total)
            self.assertEqual(2, len(first_page.items))
            self.assertEqual(1, len(second_page.items))
            self.assertEqual(created[-1].bet_id, first_page.items[0].bet_id)
            self.assertEqual(created[0].bet_id, main.get_bet(created[0].bet_id, self.user_id, db).bet_id)

            with self.assertRaises(HTTPException) as hidden:
                main.get_bet(foreign.bet_id, self.user_id, db)
            self.assertEqual(404, hidden.exception.status_code)

    async def test_deriva_estados_financeiros_e_aguardando_apuracao(self) -> None:
        with self.sessions() as db:
            processing = await main.create_bet(
                main.BetCreateRequest(**self.payload()), "processing-key", self.user_id, db
            )
            refused = await main.create_bet(
                main.BetCreateRequest(**self.payload(home=4)), "refused-key", self.user_id, db
            )
            bet = db.get(Bet, str(processing.bet_id))
            self.assertEqual("PROCESSING", main.to_bet_response(bet, db.get(MatchSnapshot, bet.match_id)).status)

            main.handle_wallet_event(db, "PAYMENT_ACCEPTED", {"betId": bet.id})
            db.commit()
            self.assertEqual(1, main.list_bets("CONFIRMED", 0, 10, self.user_id, db).total)

            snapshot = db.get(MatchSnapshot, bet.match_id)
            snapshot.status = "FINISHED"
            db.commit()
            settled = main.list_bets("AWAITING_SETTLEMENT", 0, 10, self.user_id, db)
            self.assertEqual(1, settled.total)
            self.assertEqual("AWAITING_SETTLEMENT", settled.items[0].status)

            refused_bet = db.get(Bet, str(refused.bet_id))
            main.handle_wallet_event(db, "PAYMENT_REFUSED", {"betId": refused_bet.id})
            db.commit()
            self.assertEqual("PAYMENT_REFUSED", main.get_bet(refused.bet_id, self.user_id, db).status)


if __name__ == "__main__":
    unittest.main()
