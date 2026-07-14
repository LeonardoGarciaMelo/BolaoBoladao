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

## Autenticação e borda HTTP

O `user-service` é o dono de cadastro e credenciais. Ele armazena apenas hash
BCrypt de senha e assina access tokens JWT RS256 com uma chave privada montada
em segredo. O `api-gateway` recebe somente a chave pública e é a única borda
HTTP pública dos backends: deixa `POST /api/auth/register` e
`POST /api/auth/login` públicos e exige JWT válido para `/api/partidas/**` e
`/api/wallet/**`. As rotas `/api/admin/**` exigem `ADMIN` no gateway e novamente
no serviço de destino. `GET /api/auth/me` orienta o redirecionamento do cliente.

Na validação, o gateway confere algoritmo RS256, assinatura, issuer, audience
e expiração. Ele não encaminha cabeçalhos de identidade do cliente; acrescenta
`X-Authenticated-User-Id` a partir do `sub` validado. Em Compose,
`partidas-service`, `user-service` e `carteira-service` permanecem na rede
interna e não publicam portas HTTP no host.

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
- start, end : OffsetDateTime (persistido como TIMESTAMPTZ/UTC)
- status : SCHEDULED | IN_PROGRESS | FINISHED | CANCELED

MatchEvent (histórico interno, append-only)
- id : Long
- match : Match
- eventType : MATCH_CREATED | MATCH_STARTED | TEAM_HOME_SCORED | TEAM_AWAY_SCORED | MATCH_ENDED | MATCH_CANCELED
- teamHomeScoreAtEvent, teamAwayScoreAtEvent : Integer
- occurredAt : OffsetDateTime
```

### Máquina de estados da partida

```
SCHEDULED ──iniciar──▶ IN_PROGRESS ──encerrar──▶ FINISHED
    │                        │
    └──cancelar──────────────┴──────────────────▶ CANCELED
                             │
                             └──gol (HOME/AWAY)── (permanece IN_PROGRESS)
```

Transições inválidas retornam `409 Conflict` (ver `InvalidMatchStateException`
e `DomainExceptionMapper`).

### Evento de domínio (Kafka)

O `MatchEvent` interno é a fonte de verdade publicada no tópico `match-events`
pelo transactional outbox:

```json
// tópico: match-events
{
  "match_id": "uuid",
  "event_type": "MATCH_CREATED | MATCH_STARTED | TEAM_HOME_SCORED | TEAM_AWAY_SCORED | MATCH_ENDED | MATCH_CANCELED",
  "score": {
    "team_home": 0,
    "team_away": 0
  },
  "occurred_at": "2026-07-10T20:00:00Z",
  "actor_id": "uuid do administrador ou null",
  "reason": "justificativa ou null"
}
```

Ver `docs/adr/ADR-002-eventos-partida.md` para a decisão de quando e como
essa publicação será feita (transactional outbox vs. publicação direta
pós-commit).

## Domínio: Carteira/Pagamentos (implementado)

O `carteira-service` mantém carteiras, lançamentos e consolidações diárias em
PostgreSQL, usa Redis para cache de saldo e consome/publica eventos pelo Kafka.
Sua API REST é acessível externamente apenas por `/api/wallet/**` no gateway.
`ADMIN_CREDIT` registra créditos manuais auditáveis; `BET_REFUND` compensa um
débito de palpite e é idempotente por `betId`.

## Domínio: Apostas

A versão FastAPI persiste palpites, consome cancelamentos e resultados de
pagamento, deduplica mensagens e mantém outbox de pedidos de débito/estorno.

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

%WalletEvent   PAYMENT_ACCEPTED { bet_id } / PAYMENT_REFUSED { bet_id } / PAYMENT_REFUNDED { bet_id }
%PaymentEvent  WITHDRAW { wallet_id, amount } / DEPOSIT { wallet_id, amount }

```

O fluxo completo de cancelamento está no [ADR-004](adr/ADR-004-saga-cancelamento-estorno.md).
Os limites de contexto estão registrados em [`CONTEXT-MAP.md`](../CONTEXT-MAP.md).
