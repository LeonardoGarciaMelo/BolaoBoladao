# apostas-service

Python microservice responsible for receiving user bets and publishing `BET_CREATED` events to Kafka.

## Stack

- FastAPI
- SQLAlchemy + PostgreSQL
- aiokafka

## Environment variables

- `DATABASE_URL` (default: `postgresql+psycopg://bolao:bolao@localhost:5432/apostas_db`)
- `KAFKA_BOOTSTRAP_SERVERS` (default: `localhost:29092`)
- `KAFKA_BET_TOPIC` (default: `bet-events`)

## API

### `POST /bets`

Headers:

- `X-Authenticated-User-Id: <uuid>`

Body:

```json
{
  "match_id": "11111111-1111-1111-1111-111111111111",
  "home_team_goals": 2,
  "away_team_goals": 1,
  "stake_amount": "25.00"
}
```

Response `201`:

```json
{
  "bet_id": "22222222-2222-2222-2222-222222222222",
  "user_id": "33333333-3333-3333-3333-333333333333",
  "match_id": "11111111-1111-1111-1111-111111111111",
  "home_team_goals": 2,
  "away_team_goals": 1,
  "stake_amount": "25.00",
  "status": "CREATED",
  "created_at": "2026-07-14T20:00:00Z"
}
```

Published Kafka event:

```json
{
  "eventId": "44444444-4444-4444-4444-444444444444",
  "eventType": "BET_CREATED",
  "betId": "22222222-2222-2222-2222-222222222222",
  "userId": "33333333-3333-3333-3333-333333333333",
  "amount": "25.00"
}
```
