# Auto-avaliação — Bolão Boladão

Grupo: <preencher com os 4 integrantes e papéis>

Tema/domínio: Plataforma de bolão de apostas em partidas de futebol — cadastro
de partidas/times, palpites, apuração e carteira digital dos usuários.

Perfil de execução: A · Fallbacks usados: Perfil B documentado no README para
quem não tiver Docker disponível.

> **Nota:** este arquivo reflete apenas o que está implementado até agora
> (`partidas-service`). Os demais integrantes devem atualizar cada seção à
> medida que `apostas-service`, `usuarios-service` e `carteira-service` forem
> entregues, e revisar os níveis para refletir o projeto completo antes da
> banca.

## Evidências por critério

### 1. Decomposição de domínio — nível auto-atribuído: **1 (Básico)**
Evidência: 4 bounded contexts definidos e documentados em
`docs/arquitetura.md` e `docs/adr/ADR-001-decomposicao-dominio.md`, mas
apenas 1 dos 4 serviços (`partidas-service`) está implementado com base de
dados própria e segregada (`partidas_db`, ver
`partidas-service/src/main/resources/db/migration/V1__create_partidas_schema.sql`).
Sobe para Proficiente/Avançado quando os outros 3 serviços existirem, cada
um com seu próprio banco, sem acesso cruzado.

### 2. Comunicação assíncrona — nível: **2 (Proficiente)**
Evidência: O `partidas-service` implementa o padrão **Transactional Outbox** em [MatchOutboxRelay.java](file:///home/leonardogm/Projetos/BolaoBoladao/bolao-boladao/partidas-service/src/main/java/br/com/bolaoboladao/partidas/service/MatchOutboxRelay.java). Os eventos são gravados na tabela `match_event` sob a mesma transação da partida e um scheduler periódico (`@Scheduled`) os lê e publica no tópico Kafka `match-events` com confirmação de recebimento (Ack) para garantir entrega *at-least-once*.

### 3. Idempotência e consistência — nível: **2 (Proficiente)**
Evidência: Transições de estado são transacionais (`QuarkusTransaction.requiringNew()`) e validadas localmente. No outbox pattern, cada evento publicado no Kafka carrega um `event_id` único e estável (a chave primária do evento no banco de dados), permitindo que os consumidores (Apostas/Carteira) façam deduplicação idempotente confiável.

### 4. Cache — nível: **2 (Proficiente)**
Evidência: Implementado padrão **Cache-Aside** explícito utilizando Redis em [MatchCache.java](file:///home/leonardogm/Projetos/BolaoBoladao/bolao-boladao/partidas-service/src/main/java/br/com/bolaoboladao/partidas/cache/MatchCache.java) para consultas de partidas.
- Invalidação ativa (evict) ao atualizar a partida (gols, início e encerramento).
- **TTL com Jitter** configurado para evitar o problema de *Cache Stampede / Thundering Herd*.
- **Sem cache de 404** (ausência transitória), evitando propagação de falhas e inconsistências no cliente.

### 5. Resiliência — nível: **0 (Insuficiente)**
Evidência: nenhuma ainda. Sem chamadas a serviços externos síncronas neste serviço até o momento (não há o que ter retry/circuit breaker/DLQ ainda).

### 6. Testabilidade — nível: **2 (Proficiente)**
Evidência: Testes de integração em [MatchResourceTest.java](file:///home/leonardogm/Projetos/BolaoBoladao/bolao-boladao/partidas-service/src/test/java/br/com/bolaoboladao/partidas/MatchResourceTest.java) cobrindo tanto o comportamento do cache Redis (Miss, População, Evict no update) quanto o comportamento do scheduler do Outbox Relay (verificando se o evento foi transmitido e marcado como publicado com sucesso). Os testes usam Quarkus DevServices para provisionamento automático.

### 7. Decisões arquiteturais — nível: **2 (Proficiente)**
Evidência: [ADR-001](file:///home/leonardogm/Projetos/BolaoBoladao/bolao-boladao/docs/adr/ADR-001-decomposicao-dominio.md) (com alternativas consideradas e rejeitadas) e [ADR-002](file:///home/leonardogm/Projetos/BolaoBoladao/bolao-boladao/docs/adr/ADR-002-eventos-partida.md) (decisão de outbox com entrega at-least-once, agora implementada e aceita).

### 8. Uso crítico de IA — nível: <preencher pelo grupo>
Como usamos IA e o que validamos manualmente: <descrever honestamente — por exemplo: "Usamos a IA para modelar a classe MatchCache e propor a lógica do MatchOutboxRelay utilizando os padrões de Ack/Nack reativos do SmallRye. Validamos manualmente a compilação do código Maven e a configuração das imagens Docker do Redis e Kafka no docker-compose.yml">.

### 9. Execução — nível: **2 (Proficiente)**
Como rodar: `docker compose up --build` sobe todo o ambiente do perfil A automaticamente: banco PostgreSQL (`partidas-db`), cache Redis (`partidas-cache`), broker Kafka (`kafka` em KRaft) e o serviço `partidas-service`.

## Opcionais entregues

Nenhum ainda (grupo de 4 pessoas → CORE é o mínimo obrigatório; opcionais a definir conforme sobrar tempo).
