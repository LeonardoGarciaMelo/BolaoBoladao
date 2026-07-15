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

 Carteira ──HTTP──▶ Boladão Pay Sandbox (Phoenix + Postgres + Oban)
 Carteira ◀─webhook HMAC───────────────────────────────────────┘
```

Cada serviço possui sua própria base relacional — não há acesso direto de um
serviço ao banco de outro (ADR-001). A comunicação entre domínios acontece
via eventos publicados no Kafka.

O Boladão Pay Sandbox fica fora dos quatro bounded contexts. Ele simula um
provedor externo por HTTP/webhook e possui banco próprio, mas nunca acessa os
schemas centrais e não participa do barramento Kafka.

## Autenticação e borda HTTP

O `user-service` é o dono de cadastro e credenciais. Ele armazena apenas hash
BCrypt de senha e assina access tokens JWT RS256 com uma chave privada montada
em segredo. O `api-gateway` recebe somente a chave pública e é a única borda
HTTP pública dos backends: deixa `POST /api/auth/register` e
`POST /api/auth/login` públicos e exige JWT válido para `/api/partidas/**`,
`/api/bets/**` e `/api/wallet/**`. As rotas `/api/admin/**` exigem `ADMIN` no
gateway e novamente no serviço de destino. `GET /api/auth/me` orienta o
redirecionamento do cliente.

Na validação, o gateway confere algoritmo RS256, assinatura, issuer, audience
e expiração. Ele não encaminha cabeçalhos de identidade do cliente; acrescenta
`X-Authenticated-User-Id` a partir do `sub` validado. Em Compose,
`partidas-service`, `apostas-service`, `user-service` e `carteira-service`
permanecem na rede interna e não publicam portas HTTP no host.

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
  "event_id": "uuid-da-partida:sequência-local",
  "match_id": "uuid",
  "event_type": "MATCH_CREATED | MATCH_STARTED | TEAM_HOME_SCORED | TEAM_AWAY_SCORED | MATCH_ENDED | MATCH_CANCELED",
  "team_home": "nome do mandante",
  "team_away": "nome do visitante",
  "scheduled_start": "2026-07-10T20:00:00Z",
  "score": {
    "team_home": 0,
    "team_away": 0
  },
  "occurred_at": "2026-07-10T20:00:00Z",
  "actor_id": "uuid do administrador ou null",
  "reason": "justificativa ou null"
}
```

`event_id` é uma string globalmente única no formato
`<matchId>:<sequenceId>`. Apostas mantém compatibilidade de leitura com os IDs
numéricos antigos e inclui `match_id` na chave de deduplicação desses eventos.
Grupos novos do consumidor leem o tópico desde o primeiro offset disponível.
No perfil de testes Java, a publicação é isolada em `match-events-test`.

Ver `docs/adr/ADR-002-eventos-partida.md` para a decisão de quando e como
essa publicação será feita (transactional outbox vs. publicação direta
pós-commit).

Para leitura do painel, `GET /partidas/catalog` oferece catálogo ordenado e
paginado nas views `OPEN`, `LIVE`, `FINISHED` e `CANCELED`. O campo
`bettingOpen` é derivado no instante da leitura e só é verdadeiro quando a
partida está `SCHEDULED` e o horário previsto ainda é futuro.

## Domínio: Carteira/Pagamentos (implementado)

O `carteira-service` mantém carteiras, lançamentos e consolidações diárias em
PostgreSQL, usa Redis para cache de saldo e consome/publica eventos pelo Kafka.
Sua API REST é acessível externamente apenas por `/api/wallet/**` no gateway.
`ADMIN_CREDIT` registra créditos manuais auditáveis; `BET_REFUND` compensa um
débito de palpite e é idempotente por `betId`.

`GET /wallet/me` obtém ou cria idempotentemente a carteira do usuário
autenticado e retorna o saldo em centavos. `GET /wallet/me/statement` pagina o
ledger desse mesmo usuário, sem receber `userId` ou `walletId` do cliente. A
decisão de débito continua assíncrona e serializada por carteira.

### Depósitos PIX fictícios

A criação de depósito é dividida em duas fases: a Carteira persiste
`CREATING`, chama `POST /api/v1/merchant/charges` com a chave externa estável
`deposit:<depositId>` e então anexa cobrança, checkout e expiração em
`PENDING`. Um timeout não apaga a solicitação; retry e reconciliação retomam a
mesma cobrança.

```
Web -> Gateway -> Carteira -> Boladão Pay Sandbox
                         <- webhook PAID/REFUSED/EXPIRED
                 lock carteira + validar referência/valor
                 PAID -> DEPOSIT/CREDIT exatamente uma vez
```

Webhook e `POST /wallet/me/deposits/{id}/reconcile` usam o mesmo processador
transacional. `PAID` cria o ledger com `deposit-credit:<depositId>`;
`REFUSED` e `EXPIRED` apenas encerram a solicitação. O token do checkout fica
no fragmento da URL e é enviado ao provedor como `Authorization: Checkout`,
sem passar por logs HTTP. A decisão está no [ADR-005](adr/ADR-005-provedor-pagamento-sandbox.md).

## Domínio: Apostas (implementado)

A versão FastAPI persiste palpites, mantém uma projeção local das partidas,
consome cancelamentos e resultados de pagamento, deduplica mensagens e mantém
outbox de pedidos de débito/estorno. A projeção é atualizada por todos os
eventos de Partidas e preserva times, horário previsto, status e placar.

```
Apostas
@bet
- id : uuid
- match : match
- user_id : uuid
- team_home_score, team_away_score : int
- bet_amount : Decimal
- idempotency_key : String (única por usuário)
- status persistido : CREATED | CONFIRMED | PAYMENT_REFUSED | CANCEL_PENDING |
  REFUND_PENDING | CANCELED | REFUND_FAILED

@match_snapshot
- match_id, team_home, team_away, scheduled_start
- status, home_team_goals, away_team_goals

%BetEvent
BET_CREATED { bet_id, user_id, match_id, team_home_score, team_away_score, bet_amount }
BET_REFUND_REQUESTED { bet_id, user_id, amount }

Carteira/Pagamentos
@wallet        - id, user_id
@daily_balance - id, wallet, balance
@ledger        - id, wallet, reason (WIN|DEPOSIT|BET|WITHDRAW|ADMIN_CREDIT|BET_REFUND), operation (CREDIT|DEBIT), amount

%WalletEvent   PAYMENT_ACCEPTED { bet_id } / PAYMENT_REFUSED { bet_id } / PAYMENT_REFUNDED { bet_id }
%PaymentEvent  WITHDRAW { wallet_id, amount } / DEPOSIT { wallet_id, amount }

```

`POST /bets` exige `Idempotency-Key`. Repetir chave e payload devolve o
palpite original; repetir a chave com outro payload retorna `409`. Chaves
diferentes permitem palpites idênticos. A criação só é aceita quando existe
projeção, a partida está `SCHEDULED`, o início ainda é futuro e o valor é de
pelo menos R$ 1,00.

As leituras `GET /bets` e `GET /bets/{id}` são sempre limitadas ao proprietário
derivado do JWT. A API apresenta os estados `PROCESSING`, `CONFIRMED`,
`AWAITING_SETTLEMENT`, `PAYMENT_REFUSED`, `CANCELING`, `REFUNDING`, `CANCELED`
e `REFUND_FAILED`. `AWAITING_SETTLEMENT` é derivado para um palpite confirmado
quando a projeção da partida termina; este incremento não apura nem paga
prêmios.

O fluxo completo de cancelamento está no [ADR-004](adr/ADR-004-saga-cancelamento-estorno.md).
Os limites de contexto estão registrados em [`CONTEXT-MAP.md`](../CONTEXT-MAP.md).
