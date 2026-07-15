import asyncio
import json
import logging
from contextlib import suppress
from datetime import datetime, timezone
from decimal import Decimal
from pathlib import Path
from typing import Any
from uuid import UUID, uuid4

import jwt
from aiokafka import AIOKafkaConsumer
from fastapi import Depends, FastAPI, Header, HTTPException, Query, status
from sqlalchemy import and_, func, or_, select, text
from sqlalchemy.orm import Session

from app.config import settings
from app.database import SessionLocal, get_db, init_db
from app.kafka_publisher import KafkaPublisher
from app.models import Bet, MatchCancellation, MatchSnapshot, OutboxEvent, ProcessedEvent
from app.schemas import BetCreateRequest, BetPageResponse, BetResponse, MatchSummary, RefundProgressResponse

app = FastAPI(title=settings.app_name)
logger = logging.getLogger(__name__)


def now() -> datetime:
    return datetime.now(timezone.utc)


@app.on_event("startup")
async def startup() -> None:
    init_db()
    publisher = KafkaPublisher(settings.kafka_bootstrap_servers, settings.kafka_bet_topic)
    await publisher.start()
    app.state.kafka_publisher = publisher
    app.state.loop = asyncio.get_running_loop()
    app.state.stop_event = asyncio.Event()
    app.state.tasks = [
        asyncio.create_task(consume_topic(settings.kafka_match_topic, "apostas-match-group")),
        asyncio.create_task(consume_topic(settings.kafka_wallet_topic, "apostas-wallet-group")),
        asyncio.create_task(relay_outbox()),
    ]


@app.on_event("shutdown")
async def shutdown() -> None:
    app.state.stop_event.set()
    for task in app.state.tasks:
        task.cancel()
    for task in app.state.tasks:
        with suppress(asyncio.CancelledError):
            await task
    publisher: KafkaPublisher = app.state.kafka_publisher
    await publisher.stop()


def get_authenticated_user_id(
    x_authenticated_user_id: str | None = Header(default=None, alias="X-Authenticated-User-Id"),
) -> UUID:
    if not x_authenticated_user_id:
        raise HTTPException(status_code=401, detail="Missing authenticated user")
    try:
        return UUID(x_authenticated_user_id)
    except ValueError as error:
        raise HTTPException(status_code=401, detail="Invalid authenticated user") from error


def require_admin(authorization: str | None = Header(default=None)) -> dict[str, Any]:
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="Missing bearer token")
    try:
        public_key = Path(settings.jwt_public_key_location).read_text(encoding="utf-8")
        claims = jwt.decode(authorization[7:], public_key, algorithms=["RS256"],
                            audience=settings.jwt_audience, issuer=settings.jwt_issuer)
    except Exception as error:
        raise HTTPException(status_code=401, detail="Invalid bearer token") from error
    roles = set(claims.get("groups", [])) | set(claims.get("roles", []))
    if "ADMIN" not in roles:
        raise HTTPException(status_code=403, detail="Admin role required")
    return claims


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/bets", response_model=BetResponse, status_code=status.HTTP_201_CREATED)
async def create_bet(request: BetCreateRequest,
                     idempotency_key: str | None = Header(default=None, alias="Idempotency-Key"),
                     user_id: UUID = Depends(get_authenticated_user_id),
                     db: Session = Depends(get_db)) -> BetResponse:
    if not idempotency_key or not idempotency_key.strip():
        raise HTTPException(status_code=400, detail="Idempotency-Key is required")
    idempotency_key = idempotency_key.strip()
    lock_idempotency(db, user_id, idempotency_key)
    existing = db.scalar(select(Bet).where(Bet.user_id == str(user_id), Bet.idempotency_key == idempotency_key))
    if existing is not None:
        if not same_bet(existing, request):
            raise HTTPException(status_code=409, detail="Idempotency-Key already used with another payload")
        snapshot = db.get(MatchSnapshot, existing.match_id)
        return to_bet_response(existing, snapshot)

    lock_match(db, request.match_id)
    snapshot = db.get(MatchSnapshot, str(request.match_id))
    if snapshot is None:
        raise HTTPException(status_code=409, detail="Match is not available for bets")
    scheduled_start = as_utc(snapshot.scheduled_start)
    if snapshot.status != "SCHEDULED" or scheduled_start <= now():
        raise HTTPException(status_code=409, detail="Betting window is closed")
    bet_id = uuid4()
    created_at = now()
    bet = Bet(id=str(bet_id), user_id=str(user_id), match_id=str(request.match_id),
              home_team_goals=request.home_team_goals, away_team_goals=request.away_team_goals,
              stake_amount=request.stake_amount, status="CREATED", created_at=created_at, updated_at=created_at,
              idempotency_key=idempotency_key)
    db.add(bet)
    enqueue(db, "BET_CREATED", bet)
    db.commit()
    return to_bet_response(bet, snapshot)


@app.get("/bets", response_model=BetPageResponse)
def list_bets(status_filter: str | None = Query(default=None, alias="status"), page: int = 0, size: int = 10,
              user_id: UUID = Depends(get_authenticated_user_id), db: Session = Depends(get_db)) -> BetPageResponse:
    safe_page = max(0, page)
    safe_size = max(1, min(50, size))
    condition = presentation_status_condition(status_filter)
    base_conditions = [Bet.user_id == str(user_id)]
    if condition is not None:
        base_conditions.append(condition)
    query = (select(Bet, MatchSnapshot)
             .join(MatchSnapshot, MatchSnapshot.match_id == Bet.match_id)
             .where(*base_conditions)
             .order_by(Bet.created_at.desc()))
    rows = db.execute(query.offset(safe_page * safe_size).limit(safe_size)).all()
    total = db.scalar(select(func.count(Bet.id)).select_from(Bet)
                      .join(MatchSnapshot, MatchSnapshot.match_id == Bet.match_id)
                      .where(*base_conditions)) or 0
    return BetPageResponse(items=[to_bet_response(bet, snapshot) for bet, snapshot in rows],
                           page=safe_page, size=safe_size, total=total)


@app.get("/bets/{bet_id}", response_model=BetResponse)
def get_bet(bet_id: UUID, user_id: UUID = Depends(get_authenticated_user_id),
            db: Session = Depends(get_db)) -> BetResponse:
    row = db.execute(select(Bet, MatchSnapshot)
                     .join(MatchSnapshot, MatchSnapshot.match_id == Bet.match_id)
                     .where(Bet.id == str(bet_id), Bet.user_id == str(user_id))).first()
    if row is None:
        raise HTTPException(status_code=404, detail="Bet not found")
    return to_bet_response(row[0], row[1])


def lock_idempotency(db: Session, user_id: UUID, key: str) -> None:
    if db.bind is not None and db.bind.dialect.name == "postgresql":
        db.execute(text("SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))"),
                   {"key": f"{user_id}:{key}"})


def same_bet(bet: Bet, request: BetCreateRequest) -> bool:
    return (bet.match_id == str(request.match_id)
            and bet.home_team_goals == request.home_team_goals
            and bet.away_team_goals == request.away_team_goals
            and bet.stake_amount == request.stake_amount)


def as_utc(value: datetime) -> datetime:
    return value.replace(tzinfo=timezone.utc) if value.tzinfo is None else value.astimezone(timezone.utc)


def display_status(bet: Bet, snapshot: MatchSnapshot) -> str:
    if bet.status == "CREATED":
        return "PROCESSING"
    if bet.status == "CONFIRMED" and snapshot.status == "FINISHED":
        return "AWAITING_SETTLEMENT"
    return {
        "CONFIRMED": "CONFIRMED",
        "PAYMENT_REFUSED": "PAYMENT_REFUSED",
        "CANCEL_PENDING": "CANCELING",
        "REFUND_PENDING": "REFUNDING",
        "CANCELED": "CANCELED",
        "REFUND_FAILED": "REFUND_FAILED",
    }.get(bet.status, bet.status)


def to_bet_response(bet: Bet, snapshot: MatchSnapshot | None) -> BetResponse:
    if snapshot is None:
        raise HTTPException(status_code=409, detail="Match projection is unavailable")
    return BetResponse(
        bet_id=UUID(bet.id), user_id=UUID(bet.user_id), match_id=UUID(bet.match_id),
        home_team_goals=bet.home_team_goals, away_team_goals=bet.away_team_goals,
        stake_amount=bet.stake_amount, status=display_status(bet, snapshot),
        created_at=as_utc(bet.created_at), updated_at=as_utc(bet.updated_at),
        match=MatchSummary(match_id=UUID(snapshot.match_id), team_home=snapshot.team_home,
                           team_away=snapshot.team_away, scheduled_start=as_utc(snapshot.scheduled_start),
                           status=snapshot.status, home_team_goals=snapshot.home_team_goals,
                           away_team_goals=snapshot.away_team_goals),
    )


def presentation_status_condition(value: str | None):
    if value is None or not value.strip():
        return None
    conditions = []
    mapping = {
        "PROCESSING": Bet.status == "CREATED",
        "CONFIRMED": and_(Bet.status == "CONFIRMED", MatchSnapshot.status != "FINISHED"),
        "AWAITING_SETTLEMENT": and_(Bet.status == "CONFIRMED", MatchSnapshot.status == "FINISHED"),
        "PAYMENT_REFUSED": Bet.status == "PAYMENT_REFUSED",
        "CANCELING": Bet.status == "CANCEL_PENDING",
        "REFUNDING": Bet.status == "REFUND_PENDING",
        "CANCELED": Bet.status == "CANCELED",
        "REFUND_FAILED": Bet.status == "REFUND_FAILED",
    }
    for requested in value.upper().split(","):
        condition = mapping.get(requested.strip())
        if condition is not None:
            conditions.append(condition)
    return or_(*conditions) if conditions else text("1 = 0")


@app.get("/admin/matches/{match_id}/refunds", response_model=RefundProgressResponse)
def refund_progress(match_id: UUID, _: dict[str, Any] = Depends(require_admin),
                    db: Session = Depends(get_db)) -> RefundProgressResponse:
    cancellation = db.get(MatchCancellation, str(match_id))
    if cancellation is None:
        raise HTTPException(status_code=404, detail="Cancellation not found")
    return build_refund_progress(match_id, db)


@app.post("/admin/matches/{match_id}/refunds/retry", response_model=RefundProgressResponse)
def retry_failed_refunds(match_id: UUID, _: dict[str, Any] = Depends(require_admin),
                         db: Session = Depends(get_db)) -> RefundProgressResponse:
    lock_match(db, match_id)
    if db.get(MatchCancellation, str(match_id)) is None:
        raise HTTPException(status_code=404, detail="Cancellation not found")
    failed = db.scalars(select(Bet).where(Bet.match_id == str(match_id), Bet.status == "REFUND_FAILED")
                        .with_for_update()).all()
    for bet in failed:
        bet.status = "REFUND_PENDING"
        bet.updated_at = now()
        enqueue(db, "BET_REFUND_REQUESTED", bet)
    db.commit()
    return build_refund_progress(match_id, db)


def build_refund_progress(match_id: UUID, db: Session) -> RefundProgressResponse:
    rows = db.execute(select(Bet.status, func.count(Bet.id)).where(Bet.match_id == str(match_id)).group_by(Bet.status)).all()
    counts = {row[0]: row[1] for row in rows}
    total = sum(counts.values())
    completed = counts.get("CANCELED", 0) + counts.get("PAYMENT_REFUSED", 0)
    failed = counts.get("REFUND_FAILED", 0)
    pending = total - completed - failed
    aggregate = "FAILED" if failed else ("COMPLETED" if pending == 0 else "PROCESSING")
    return RefundProgressResponse(match_id=match_id, status=aggregate, total=total,
                                  pending=pending, completed=completed, failed=failed)


def enqueue(db: Session, event_type: str, bet: Bet) -> None:
    payload = {"eventId": str(uuid4()), "eventType": event_type, "betId": bet.id,
               "userId": bet.user_id, "amount": str(bet.stake_amount)}
    db.add(OutboxEvent(id=str(uuid4()), topic=settings.kafka_bet_topic, event_key=bet.id,
                       payload=json.dumps(payload), created_at=now(), published_at=None))


def lock_match(db: Session, match_id: UUID | str) -> None:
    if db.bind is not None and db.bind.dialect.name == "postgresql":
        db.execute(text("SELECT pg_advisory_xact_lock(hashtextextended(:match_id, 0))"),
                   {"match_id": str(match_id)})


async def relay_outbox() -> None:
    while not app.state.stop_event.is_set():
        try:
            await asyncio.to_thread(relay_batch)
            await asyncio.sleep(1)
        except asyncio.CancelledError:
            raise
        except Exception:
            logger.exception("Falha ao publicar lote do outbox; uma nova tentativa será feita")
            await asyncio.sleep(2)


def relay_batch() -> None:
    with SessionLocal() as db:
        events = db.scalars(select(OutboxEvent).where(OutboxEvent.published_at.is_(None))
                            .order_by(OutboxEvent.created_at).limit(50)).all()
        for event in events:
            future = asyncio.run_coroutine_threadsafe(
                app.state.kafka_publisher.publish(json.loads(event.payload), event.event_key, event.topic),
                app.state.loop,
            )
            future.result(timeout=10)
            event.published_at = now()
            db.commit()


async def consume_topic(topic: str, group_id: str) -> None:
    while not app.state.stop_event.is_set():
        consumer = AIOKafkaConsumer(topic, bootstrap_servers=settings.kafka_bootstrap_servers,
                                    group_id=group_id, enable_auto_commit=False,
                                    value_deserializer=lambda value: json.loads(value.decode("utf-8")))
        started = False
        try:
            await consumer.start()
            started = True
            async for message in consumer:
                await asyncio.to_thread(handle_event, topic, message.value)
                await consumer.commit()
        except asyncio.CancelledError:
            raise
        except Exception:
            logger.exception("Consumidor %s falhou; reiniciando sem avançar o offset", group_id)
            await asyncio.sleep(2)
        finally:
            if started:
                await consumer.stop()


def handle_event(topic: str, event: dict[str, Any]) -> None:
    event_type = event.get("event_type") or event.get("eventType")
    event_id = event.get("event_id") or f"{event_type}:{event.get('bet_id') or event.get('betId')}"
    processed_id = f"{topic}:{event_id}"
    with SessionLocal() as db:
        if db.get(ProcessedEvent, processed_id) is not None:
            return
        if topic == settings.kafka_match_topic:
            handle_match_event(db, event_type, event)
        elif topic == settings.kafka_wallet_topic:
            handle_wallet_event(db, event_type, event)
        db.add(ProcessedEvent(id=processed_id, processed_at=now()))
        db.commit()


def handle_match_event(db: Session, event_type: str | None, event: dict[str, Any]) -> None:
    match_id = str(event["match_id"])
    lock_match(db, match_id)
    snapshot = db.get(MatchSnapshot, match_id)
    scheduled_start = event.get("scheduled_start")
    if snapshot is not None or (scheduled_start and event.get("team_home") and event.get("team_away")):
        if snapshot is None:
            snapshot = MatchSnapshot(
                match_id=match_id,
                team_home=str(event["team_home"]),
                team_away=str(event["team_away"]),
                scheduled_start=parse_datetime(str(scheduled_start)),
                status="SCHEDULED",
                home_team_goals=0,
                away_team_goals=0,
                updated_at=now(),
            )
            db.add(snapshot)
        else:
            if event.get("team_home"):
                snapshot.team_home = str(event["team_home"])
            if event.get("team_away"):
                snapshot.team_away = str(event["team_away"])
            if scheduled_start:
                snapshot.scheduled_start = parse_datetime(str(scheduled_start))
        score = event.get("score") or {}
        snapshot.home_team_goals = int(score.get("team_home", snapshot.home_team_goals))
        snapshot.away_team_goals = int(score.get("team_away", snapshot.away_team_goals))
        snapshot.status = {
            "MATCH_CREATED": "SCHEDULED",
            "MATCH_STARTED": "IN_PROGRESS",
            "MATCH_ENDED": "FINISHED",
            "MATCH_CANCELED": "CANCELED",
        }.get(event_type, snapshot.status)
        snapshot.updated_at = now()
    if event_type == "MATCH_CANCELED":
        handle_match_canceled(db, event)


def parse_datetime(value: str) -> datetime:
    return as_utc(datetime.fromisoformat(value.replace("Z", "+00:00")))


def handle_match_canceled(db: Session, event: dict[str, Any]) -> None:
    match_id = str(event["match_id"])
    if db.get(MatchCancellation, match_id) is None:
        db.add(MatchCancellation(match_id=match_id, reason=event.get("reason") or "Cancelada pelo administrador",
                                 canceled_at=now()))
    bets = db.scalars(select(Bet).where(Bet.match_id == match_id).with_for_update()).all()
    for bet in bets:
        if bet.status == "CONFIRMED":
            bet.status = "REFUND_PENDING"
            enqueue(db, "BET_REFUND_REQUESTED", bet)
        elif bet.status == "CREATED":
            bet.status = "CANCEL_PENDING"
        bet.updated_at = now()


def handle_wallet_event(db: Session, event_type: str | None, event: dict[str, Any]) -> None:
    bet_id = str(event.get("bet_id") or event.get("betId"))
    bet = db.scalar(select(Bet).where(Bet.id == bet_id).with_for_update())
    if bet is None:
        return
    if event_type == "PAYMENT_ACCEPTED":
        if bet.status == "CANCEL_PENDING":
            bet.status = "REFUND_PENDING"
            enqueue(db, "BET_REFUND_REQUESTED", bet)
        elif bet.status == "CREATED":
            bet.status = "CONFIRMED"
    elif event_type == "PAYMENT_REFUSED" and bet.status == "CREATED":
        bet.status = "PAYMENT_REFUSED"
    elif event_type == "PAYMENT_REFUSED" and bet.status == "CANCEL_PENDING":
        bet.status = "CANCELED"
    elif event_type == "PAYMENT_REFUNDED" and bet.status == "REFUND_PENDING":
        bet.status = "CANCELED"
    elif event_type == "PAYMENT_REFUND_FAILED" and bet.status == "REFUND_PENDING":
        bet.status = "REFUND_FAILED"
    bet.updated_at = now()
