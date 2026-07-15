# Auto-avaliação — Bolão Boladão

Grupo: Leonardo Garcia, Renan Gregório, Lucas Guimarães, André Corradi (revezamento de papéis)

Tema/domínio: Plataforma de bolão de apostas em partidas de futebol — cadastro
de partidas/times, palpites, apuração e carteira digital dos usuários.

Perfil de execução: A · Fallbacks usados: Perfil B documentado no README para
quem não tiver Docker disponível.

> **Nota:** este arquivo reflete a versão completa do projeto. Os 4 serviços principais (`partidas-service`, `apostas-service`, `user-service` e `carteira-service`) e o `api-gateway` estão implementados e funcionando conforme os critérios definidos.

## Evidências por critério

### 1. Decomposição de domínio — nível auto-atribuído: **3 (Avançado)**

Evidência: 4 bounded contexts definidos e documentados em
[`docs/arquitetura.md`](docs/arquitetura.md) e [`ADR-001`](docs/adr/ADR-001-decomposicao-dominio.md).
Todos os 4 microsserviços centrais (`partidas-service`, `apostas-service`, `user-service` e `carteira-service`) estão 100% implementados, cada
um com seu próprio banco isolado, sem acesso cruzado (ver o [`docker-compose.yml`](docker-compose.yml) e diretórios `src/main/resources/db/migration` de cada serviço Java e `app/models.py` do apostas-service).
O mapa de contextos está em [`CONTEXT-MAP.md`](CONTEXT-MAP.md) e a linguagem
ubíqua em [`CONTEXT.md`](CONTEXT.md).

### 2. Comunicação assíncrona — nível: **3 (Avançado)**

Evidência: O `partidas-service` e o `apostas-service` implementam o padrão **Transactional Outbox**:
- `partidas-service`: os eventos são gravados na tabela `match_event` sob a mesma transação da partida e um scheduler periódico (`@Scheduled` em [`MatchOutboxRelay`](partidas-service/src/main/java/br/com/bolaoboladao/partidas/service/MatchOutboxRelay.java)) os lê e publica no tópico Kafka `match-events` com confirmação de recebimento (Ack), garantindo entrega *at-least-once*.
- `apostas-service`: mantém outbox de pedidos de débito/estorno em [`OutboxEvent`](apostas-service/app/models.py), com relay assíncrono em [`main.py (relay_outbox)`](apostas-service/app/main.py).
- `carteira-service`: consome `bet-events` e `user-events` e publica resultados em `wallet-events`.

**Garantia de entrega declarada:** *at-least-once* via Transactional Outbox + consumidores idempotentes (deduplicação por `event_id`/`ProcessedEvent`).

### 3. Idempotência e consistência — nível: **3 (Avançado)**

Evidência: Transições de estado são transacionais e validadas localmente. Mecanismos de idempotência implementados:
- **Outbox pattern**: cada evento publicado no Kafka carrega um `event_id` único e estável, permitindo deduplicação confiável pelos consumidores.
- **Idempotency-Key HTTP**: rotas de criação de partida, gol, cancelamento (`partidas-service`), criação de palpite (`apostas-service`) e depósitos/créditos (`carteira-service`) exigem `Idempotency-Key`. Replay idêntico retorna o resultado original; reutilização com payload diferente retorna `409`.
- **Advisory locks PostgreSQL**: `pg_advisory_xact_lock` serializa operações concorrentes por partida e por chave de idempotência em [`main.py`](apostas-service/app/main.py).
- **SAGA coreografada com compensação**: cancelamento de partidas com estorno seguro e tolerante a falhas (ver [`ADR-004`](docs/adr/ADR-004-saga-cancelamento-estorno.md)).

Evidência de teste: [`test_cancellation_saga.py`](apostas-service/tests/test_cancellation_saga.py) valida reprocessamento sem duplicação.

### 4. Cache — nível: **3 (Avançado)**

Evidência: Implementado padrão **Cache-Aside** explícito utilizando Redis em dois serviços:
- **Partidas**: [`MatchCache.java`](partidas-service/src/main/java/br/com/bolaoboladao/partidas/cache/MatchCache.java) — catálogo de partidas cacheado com invalidação ativa (evict) ao atualizar (gols, início, encerramento). **TTL de 1h com Jitter** (variação aleatória de 0 a 5 min) para evitar *Cache Stampede / Thundering Herd*. Sem cache de 404.
- **Carteira**: [`RedisWalletCache.java`](carteira-service/src/main/java/br/com/bolaoboladao/carteira/infrastructure/cache/RedisWalletCache.java) — cache de saldo e extrato com TTL de 5 min, invalidação ativa em cada operação financeira.
- **Fallback**: se o Redis falhar, o sistema recorre automaticamente ao PostgreSQL, garantindo disponibilidade com custo de performance (implementado via `@Fallback` e `try/catch` com log).
- A decisão de design está detalhada em [`ADR-006`](docs/adr/ADR-006-estrategia-de-cache.md).

### 5. Resiliência — nível: **3 (Avançado)**

Evidência: Mecanismos de resiliência implementados em múltiplas camadas:
- **Retry com backoff**: `@Retry(delay = 2000)` no consumidor Kafka de `user-events` e `.retry().withBackOff(Duration.ofSeconds(2)).atMost(3)` no consumidor de `bet-events` ([`KafkaEventConsumer.java`](carteira-service/src/main/java/br/com/bolaoboladao/carteira/presentation/messaging/KafkaEventConsumer.java)).
- **Circuit Breaker**: `@CircuitBreaker(requestVolumeThreshold = 4, delay = 5000)` em todos os consumidores Kafka e em todas as operações do Redis ([`RedisWalletCache.java`](carteira-service/src/main/java/br/com/bolaoboladao/carteira/infrastructure/cache/RedisWalletCache.java)).
- **DLQ (Dead Letter Queue)**: configurado para `bet-events` → `bet-events-dlq` e `user-events` → `user-events-dlq` em [`application.properties`](carteira-service/src/main/resources/application.properties) (`failure-strategy=dead-letter-queue`).
- **Fallback**: `@Fallback` em cada operação de cache Redis (leitura, escrita e invalidação de saldo/extrato) — degradação graciosa para PostgreSQL.
- **Reconciliação e timeout**: depósitos PIX usam reconciliação idempotente; timeout não apaga a solicitação, e `POST /wallet/me/deposits/{id}/reconcile` retoma a cobrança (ver [`ADR-005`](docs/adr/ADR-005-provedor-pagamento-sandbox.md)).
- **Saga tolerante a falhas**: estados terminais de falha (`REFUND_FAILED`) são visíveis e reprocessáveis administrativamente.

### 6. Testabilidade — nível: **3 (Avançado)**

Evidência: O projeto possui uma suíte de testes abrangente e executável:
- **Contract test (Pact)**: [`CarteiraConsumerPactTest.java`](carteira-service/src/test/java/br/com/bolaoboladao/carteira/contract/CarteiraConsumerPactTest.java) — contract test assíncrono validando que a Carteira consegue deserializar os eventos `BET_CREATED` publicados pelo `apostas-service`.
- **Testes de integração (partidas)**: [`MatchResourceTest.java`](partidas-service/src/test/java/br/com/bolaoboladao/partidas/MatchResourceTest.java) — cobertura de cache (HIT/MISS/evict), outbox relay, ciclo de vida da partida, idempotência de comandos, cancelamento e testes de catálogo.
- **Testes de unidade (partidas)**: [`MatchOutboxRelayTest.java`](partidas-service/src/test/java/br/com/bolaoboladao/partidas/service/MatchOutboxRelayTest.java), [`MatchLifecycleSchedulerTest.java`](partidas-service/src/test/java/br/com/bolaoboladao/partidas/service/MatchLifecycleSchedulerTest.java), [`MatchServiceTimeBoundaryTest.java`](partidas-service/src/test/java/br/com/bolaoboladao/partidas/service/MatchServiceTimeBoundaryTest.java), [`MatchMapperTest.java`](partidas-service/src/test/java/br/com/bolaoboladao/partidas/MatchMapperTest.java).
- **Testes de aplicação (carteira)**: [`AdminCreditUseCaseTest.java`](carteira-service/src/test/java/br/com/bolaoboladao/carteira/application/AdminCreditUseCaseTest.java), [`CreateDepositUseCaseTest.java`](carteira-service/src/test/java/br/com/bolaoboladao/carteira/application/CreateDepositUseCaseTest.java), [`ProcessDepositUseCaseTest.java`](carteira-service/src/test/java/br/com/bolaoboladao/carteira/application/ProcessDepositUseCaseTest.java), [`DepositTransactionsTest.java`](carteira-service/src/test/java/br/com/bolaoboladao/carteira/application/DepositTransactionsTest.java), [`WebhookSignatureTest.java`](carteira-service/src/test/java/br/com/bolaoboladao/carteira/infrastructure/payment/WebhookSignatureTest.java), [`CarteiraResourceAuthorizationTest.java`](carteira-service/src/test/java/br/com/bolaoboladao/carteira/presentation/rest/CarteiraResourceAuthorizationTest.java).
- **Testes do user-service**: [`AuthResourceTest.java`](user-service/src/test/java/br/com/bolaoboladao/users/AuthResourceTest.java) — cadastro, login, JWT RS256, duplicidade e roles ADMIN.
- **Testes do apostas-service**: [`test_cancellation_saga.py`](apostas-service/tests/test_cancellation_saga.py), [`test_kafka_consumer.py`](apostas-service/tests/test_kafka_consumer.py), [`test_user_bets_api.py`](apostas-service/tests/test_user_bets_api.py).
- **Testes E2E (Playwright)**: suíte executada pelo Compose via `web-e2e`, usando `http://api-gateway:8080`.
- **Testes via Compose**: `docker compose --profile test run --rm backend-tests` (Java), `apostas-tests` (Python), `payment-simulator-tests` (Elixir) e `web-e2e` (Playwright).
- Perfis de execução permitem testes sem Docker (Perfil B/C).

### 7. Decisões arquiteturais — nível: **3 (Avançado)**

Evidência: 6 ADRs documentados na pasta [`docs/adr/`](docs/adr/), cada um cobrindo contexto, alternativas consideradas e trade-offs claros:
- [ADR-001 — Decomposição de domínio](docs/adr/ADR-001-decomposicao-dominio.md)
- [ADR-002 — Publicação de eventos via Outbox](docs/adr/ADR-002-eventos-partida.md)
- [ADR-003 — Autorização e segurança](docs/adr/ADR-003-autorizacao-administrativa.md)
- [ADR-004 — SAGA de cancelamento](docs/adr/ADR-004-saga-cancelamento-estorno.md)
- [ADR-005 — Resiliência com pagamentos](docs/adr/ADR-005-provedor-pagamento-sandbox.md)
- [ADR-006 — Estratégia de Cache-Aside](docs/adr/ADR-006-estrategia-de-cache.md)

### 8. Uso crítico de IA — nível: **3 (Avançado)**

Como usamos IA e o que validamos manualmente: Utilizamos a IA extensivamente em todo o processo. No nível de **design/arquitetura**, a IA foi essencial como peer partner para modelar as classes de domínios isolados, elaborar a estratégia robusta de Transactional Outbox (como os padrões de Ack/Nack reativos do SmallRye) e refinar os diagramas de contexto. Na etapa de **desenvolvimento**, auxiliou a preencher as lacunas do código de infraestrutura (bancos, caches, mensageria via Kafka) e a montar todos os scripts DDL (Flyway).

*Validação Manual:* O grupo validou de forma independente toda a execução do ambiente pelo Compose, conferindo se os retornos idempotentes das rotas devolviam 409 quando previsto, ou se o fluxo at-least-once funcionava apagando/recriando containers sem perda de dados na tabela de outbox. A integridade do domínio financeiro de carteira também passou por revisão e testes rigorosos.

### 9. Execução — nível: **3 (Avançado)**

Como rodar: `docker compose up --build` sobe todo o ambiente da plataforma (Perfil A) automaticamente, instanciando os 4 microsserviços (`partidas-service`, `apostas-service`, `carteira-service`, `user-service`), o `api-gateway`, o painel web, o provedor PIX fictício (`payment-simulator`), além de 4 bancos PostgreSQL, cache Redis e broker Kafka (KRaft). Tudo mapeado e configurado para execução sem atritos. Instruções completas no [README.md](README.md).

## Opcionais entregues

Conforme escopo escalável (§2 do enunciado), para grupo de 4 integrantes devemos entregar ao menos 1 item opcional (1 por integrante acima de 3). Entregamos 3:

1. **SAGA de coreografia com compensação:** Implementada para cancelar partidas e estornar o saldo para os usuários afetados de forma segura e tolerante a falhas. A saga coordena Partidas → Apostas → Carteira com deduplicação, estados de falha visíveis e reprocessamento administrativo. (Evidência: [`ADR-004`](docs/adr/ADR-004-saga-cancelamento-estorno.md), [`test_cancellation_saga.py`](apostas-service/tests/test_cancellation_saga.py), [`main.py (handle_match_canceled)`](apostas-service/app/main.py)).
2. **Resiliência avançada:** `@Retry` com backoff, `@CircuitBreaker`, `@Fallback` e DLQ (`bet-events-dlq`, `user-events-dlq`) configurados no consumidor Kafka e no cache Redis. Reconciliação idempotente nos depósitos PIX. (Evidência: [`KafkaEventConsumer.java`](carteira-service/src/main/java/br/com/bolaoboladao/carteira/presentation/messaging/KafkaEventConsumer.java), [`RedisWalletCache.java`](carteira-service/src/main/java/br/com/bolaoboladao/carteira/infrastructure/cache/RedisWalletCache.java), [`application.properties`](carteira-service/src/main/resources/application.properties), [`ADR-005`](docs/adr/ADR-005-provedor-pagamento-sandbox.md)).
3. **Uso documentado e crítico de IA no design/desenvolvimento:** Conforme detalhado no Critério 8, a IA desempenhou papel estrutural do início ao fim (Evidência: README e Critério 8).
