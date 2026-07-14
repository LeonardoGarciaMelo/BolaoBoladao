from datetime import datetime, timezone
from uuid import UUID, uuid4

from fastapi import Depends, FastAPI, Header, HTTPException, status
from sqlalchemy.orm import Session

from app.config import settings
from app.database import get_db, init_db
from app.kafka_publisher import KafkaPublisher
from app.models import Bet
from app.schemas import BetCreateRequest, BetCreatedEvent, BetResponse

app = FastAPI(title=settings.app_name)


@app.on_event("startup")
async def startup() -> None:
    init_db()
    publisher = KafkaPublisher(
        bootstrap_servers=settings.kafka_bootstrap_servers,
        topic=settings.kafka_bet_topic,
    )
    await publisher.start()
    app.state.kafka_publisher = publisher


@app.on_event("shutdown")
async def shutdown() -> None:
    publisher: KafkaPublisher | None = getattr(app.state, "kafka_publisher", None)
    if publisher is not None:
        await publisher.stop()


def get_authenticated_user_id(
    x_authenticated_user_id: str | None = Header(default=None, alias="X-Authenticated-User-Id"),
) -> UUID:
    if x_authenticated_user_id is None or x_authenticated_user_id.strip() == "":
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Missing authenticated user")
    try:
        return UUID(x_authenticated_user_id)
    except ValueError as error:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid authenticated user") from error


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/bets", response_model=BetResponse, status_code=status.HTTP_201_CREATED)
async def create_bet(
    request: BetCreateRequest,
    user_id: UUID = Depends(get_authenticated_user_id),
    db: Session = Depends(get_db),
) -> BetResponse:
    bet_id = uuid4()
    now = datetime.now(timezone.utc)

    bet = Bet(
        id=str(bet_id),
        user_id=str(user_id),
        match_id=str(request.match_id),
        home_team_goals=request.home_team_goals,
        away_team_goals=request.away_team_goals,
        stake_amount=request.stake_amount,
        status="CREATED",
        created_at=now,
    )

    db.add(bet)
    db.commit()

    event = BetCreatedEvent(
        eventId=uuid4(),
        eventType="BET_CREATED",
        betId=bet_id,
        userId=user_id,
        amount=request.stake_amount,
    )

    try:
        publisher: KafkaPublisher = app.state.kafka_publisher
        await publisher.publish(
            payload={
                "eventId": str(event.eventId),
                "eventType": event.eventType,
                "betId": str(event.betId),
                "userId": str(event.userId),
                "amount": str(event.amount),
            },
            key=str(event.betId),
        )
    except Exception as error:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Bet was created, but event publishing failed",
        ) from error

    return BetResponse(
        bet_id=bet_id,
        user_id=user_id,
        match_id=request.match_id,
        home_team_goals=request.home_team_goals,
        away_team_goals=request.away_team_goals,
        stake_amount=request.stake_amount,
        status="CREATED",
        created_at=now,
    )
