from typing import Generator

from sqlalchemy import create_engine, text
from sqlalchemy.orm import Session, sessionmaker

from app.config import settings
from app.models import Base

engine = create_engine(settings.database_url, pool_pre_ping=True)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)


def init_db() -> None:
    Base.metadata.create_all(bind=engine)
    with engine.begin() as connection:
        connection.execute(text("ALTER TABLE bets ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ"))
        connection.execute(text("UPDATE bets SET updated_at = created_at WHERE updated_at IS NULL"))
        connection.execute(text("ALTER TABLE bets ALTER COLUMN updated_at SET NOT NULL"))
        connection.execute(text("ALTER TABLE bets ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(120)"))
        connection.execute(text("CREATE UNIQUE INDEX IF NOT EXISTS uq_bet_user_idempotency ON bets(user_id, idempotency_key)"))


def get_db() -> Generator[Session, None, None]:
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
