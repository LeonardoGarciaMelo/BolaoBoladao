from datetime import datetime
from decimal import Decimal
from uuid import UUID

from pydantic import BaseModel, Field


class BetCreateRequest(BaseModel):
    match_id: UUID
    home_team_goals: int = Field(ge=0, le=30)
    away_team_goals: int = Field(ge=0, le=30)
    stake_amount: Decimal = Field(ge=Decimal("1.00"), max_digits=12, decimal_places=2)


class MatchSummary(BaseModel):
    match_id: UUID
    team_home: str
    team_away: str
    scheduled_start: datetime
    status: str
    home_team_goals: int
    away_team_goals: int


class BetResponse(BaseModel):
    bet_id: UUID
    user_id: UUID
    match_id: UUID
    home_team_goals: int
    away_team_goals: int
    stake_amount: Decimal
    status: str
    created_at: datetime
    updated_at: datetime
    match: MatchSummary


class BetPageResponse(BaseModel):
    items: list[BetResponse]
    page: int
    size: int
    total: int


class BetCreatedEvent(BaseModel):
    eventId: UUID
    eventType: str
    betId: UUID
    userId: UUID
    amount: Decimal


class RefundProgressResponse(BaseModel):
    match_id: UUID
    status: str
    total: int
    pending: int
    completed: int
    failed: int
