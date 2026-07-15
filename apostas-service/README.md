# apostas-service

Serviço FastAPI responsável pelos palpites individuais. Ele projeta as
partidas a partir do Kafka, valida a janela de palpites, publica pedidos de
débito e acompanha confirmação, recusa e estorno.

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
- `Idempotency-Key: <chave opaca>`

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
  "status": "PROCESSING",
  "created_at": "2026-07-14T20:00:00Z",
  "updated_at": "2026-07-14T20:00:00Z",
  "match": {
    "match_id": "11111111-1111-1111-1111-111111111111",
    "team_home": "Aurora",
    "team_away": "Estrela",
    "scheduled_start": "2026-07-15T20:00:00Z",
    "status": "SCHEDULED",
    "home_team_goals": 0,
    "away_team_goals": 0
  }
}
```

A mesma chave com o mesmo payload retorna o registro original. A mesma chave
com payload diferente retorna `409`; outra chave permite criar outro palpite,
mesmo que o placar e o valor sejam idênticos. A partida deve existir na
projeção, permanecer `SCHEDULED` e ter horário futuro. O valor mínimo é R$ 1,00.

### `GET /bets?status=&page=0&size=10`

Lista os palpites do usuário autenticado, do mais recente para o mais antigo.
O filtro aceita um ou mais estados separados por vírgula.

### `GET /bets/{id}`

Consulta um único palpite e retorna `404` quando ele não pertence ao usuário.

Os estados apresentados são `PROCESSING`, `CONFIRMED`,
`AWAITING_SETTLEMENT`, `WON`, `LOST`, `PAYMENT_REFUSED`, `CANCELING`, `REFUNDING`, `CANCELED`
e `REFUND_FAILED`. `WON` e `LOST` são gerados na apuração quando a partida encerra,
juntamente com o envio do prêmio correspondente para a carteira do usuário no caso de vitória.

Somente um palpite `CONFIRMED` cujo placar previsto seja idêntico ao placar final
é vencedor. Se não houver acerto exato, todos perdem e nenhum `BET_SETTLED` é
publicado. Havendo um ou mais vencedores, o prêmio de cada um é calculado por
`bolão total × valor do palpite vencedor ÷ soma dos valores dos vencedores`.
O bolão total contém todos os palpites confirmados da partida. Não existe bônus,
multiplicador ou prêmio mínimo; o valor publicado em `BET_SETTLED` é o crédito
total, incluindo o valor originalmente debitado do palpite vencedor. O cálculo
é feito em centavos: sobras são atribuídas pelas maiores frações e, em caso de
empate, pelo identificador do palpite. A soma dos prêmios coincide com o bolão.

Eventos Kafka publicados usam o mesmo envelope. `BET_CREATED` solicita o débito
e `BET_SETTLED` solicita o crédito do prêmio calculado:

```json
{
  "eventId": "44444444-4444-4444-4444-444444444444",
  "eventType": "BET_CREATED",
  "betId": "22222222-2222-2222-2222-222222222222",
  "userId": "33333333-3333-3333-3333-333333333333",
  "amount": "25.00"
}
```
