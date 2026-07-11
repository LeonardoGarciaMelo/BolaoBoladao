# Arquitetura — Bolão Boladão

## Diagrama de contextos

```
                         ┌───────────────┐
        Web  ──────────▶ │               │
                         │  API Gateway  │
        Mobile ─────────▶│               │
                         └──────┬────────┘
                                │
        ┌───────────────┬──────┴───────┬────────────────────┐
        ▼                ▼              ▼                    ▼
 ┌─────────────┐  ┌─────────────┐ ┌─────────────┐  ┌───────────────────┐
 │  Partidas   │  │   Apostas   │ │  Usuários   │  │ Carteira/Pagamentos│
 │ (times,     │  │ (palpites,  │ │ (sso,       │  │ (wallet, ledger,   │
 │  resultados)│  │  apuração,  │ │  identific.)│  │  saldo diário)     │
 │             │  │  ranking)   │ │             │  │                    │
 └──────┬──────┘  └──────┬──────┘ └──────┬──────┘  └─────────┬──────────┘
        │                │               │                    │
        ▼                ▼               ▼                    ▼
   Postgres          Postgres        Postgres             Postgres
  (partidas_db)    (apostas_db)    (usuarios_db)       (carteira_db)

                         ┌─────────────┐
                         │    Kafka    │◀── barramento de eventos entre
                         └─────────────┘    os serviços acima
```

Cada serviço possui sua própria base relacional — não há acesso direto de um
serviço ao banco de outro (ADR-001). A comunicação entre domínios acontece
via eventos publicados no Kafka.

## Domínio: Partidas (implementado)

### Entidades

```
Team
- id : Long
- name : String

Match
- id : UUID
- teamHome, teamAway : Team
- teamHomeScore, teamAwayScore : Integer
- start, end : LocalDateTime
- status : SCHEDULED | IN_PROGRESS | FINISHED | CANCELED

MatchEvent (histórico interno, append-only)
- id : Long
- match : Match
- eventType : MATCH_STARTED | TEAM_HOME_SCORED | TEAM_AWAY_SCORED | MATCH_ENDED
- teamHomeScoreAtEvent, teamAwayScoreAtEvent : Integer
- occurredAt : LocalDateTime
```

### Máquina de estados da partida

```
SCHEDULED ──iniciar──▶ IN_PROGRESS ──encerrar──▶ FINISHED
                             │
                             └──gol (HOME/AWAY)── (permanece IN_PROGRESS)
```

Transições inválidas retornam `409 Conflict` (ver `InvalidMatchStateException`
e `DomainExceptionMapper`).

### Evento de domínio (Kafka) — contrato planejado, ainda não publicado

O `MatchEvent` interno é a fonte de verdade a partir da qual o serviço vai
publicar eventos no tópico `match-events` quando o fluxo assíncrono do grupo
for implementado:

```json
// tópico: match-events
{
  "match_id": "uuid",
  "event_type": "MATCH_STARTED | TEAM_HOME_SCORED | TEAM_AWAY_SCORED | MATCH_ENDED",
  "score": {
    "team_home": 0,
    "team_away": 0
  },
  "occurred_at": "2026-07-10T20:00:00"
}
```

Ver `docs/adr/ADR-002-eventos-partida.md` para a decisão de quando e como
essa publicação será feita (transactional outbox vs. publicação direta
pós-commit).

## Domínios: Apostas, Usuários, Carteira/Pagamentos

Ainda não implementados. Modelo de dados e catálogo de eventos previstos
(conforme desenho original do grupo) documentados abaixo para referência dos
próximos integrantes.

```
Apostas
@bet
- id : uuid
- match : match
- user_id : uuid
- team_home_score, team_away_score : int
- bet_amount : Decimal
- status : CREATED | CONFIRMED | CANCELED | FINISHED

%BetEvent
BET_CREATED { bet_id, user_id, match_id, team_home_score, team_away_score, bet_amount }
BET_SETTLED { bet_id, user_id, amount }

Carteira/Pagamentos
@wallet        - id, user_id
@daily_balance - id, wallet, balance
@ledger        - id, wallet, reason (WIN|DEPOSIT|BET|WITHDRAW), operation (CREDIT|DEBIT), amount

%WalletEvent   PAYMENT_ACCEPTED { bet_id } / PAYMENT_REFUSED { bet_id }
%PaymentEvent  WITHDRAW { wallet_id, amount } / DEPOSIT { wallet_id, amount }

Usuários
@user - id, name, username, password, salt

%UserEvent USER_CREATED { user_id, name }
```
