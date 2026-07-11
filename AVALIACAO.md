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

### 2. Comunicação assíncrona — nível: **0 (Insuficiente, por enquanto)**
Evidência: nenhuma. O `partidas-service` grava eventos internamente
(`match_event`, ver `Match Event.java` e `MatchService.java`), preparando o
terreno, mas ainda não publica nada no Kafka. Decisão de design já
registrada em `docs/adr/ADR-002-eventos-partida.md` (outbox pattern), a
implementar. **Este critério só evolui quando o Kafka for ligado.**

### 3. Idempotência e consistência — nível: **1 (Básico)**
Evidência: transições de estado da partida são protegidas por validação de
status atual (`MatchService`, lança `InvalidMatchStateException` em
transições inválidas) e cada mudança de estado é transacional
(`QuarkusTransaction.requiringNew()`). Ainda não há consumidor Kafka (não
existe ainda o que precisar ser idempotente nesse sentido) nem SAGA.

### 4. Cache — nível: **0 (Insuficiente)**
Evidência: nenhuma ainda. Não implementado neste serviço; candidato natural
seria cache de consulta de partidas/resultados (leitura frequente, escrita
pouco frequente) — a decidir com o grupo quem assume.

### 5. Resiliência — nível: **0 (Insuficiente)**
Evidência: nenhuma ainda. Sem chamadas a serviços externos síncronas neste
serviço até o momento (não há o que ter retry/circuit breaker/DLQ ainda).

### 6. Testabilidade — nível: **1 (Básico)**
Evidência: `partidas-service/src/test/java/.../MatchResourceTest.java` —
testes de integração via REST Assured cobrindo o fluxo feliz e 2 cenários de
erro (transição inválida → 409, recurso inexistente → 404). Rodam com
Testcontainers (perfil de teste). Ainda não há contract test (Pact) entre
serviços — só existe 1 serviço até agora, então não há par para contrato.

### 7. Decisões arquiteturais — nível: **2 (Proficiente)**
Evidência: `docs/adr/ADR-001-decomposicao-dominio.md` (com alternativas
consideradas e rejeitadas, não só descrição) e
`docs/adr/ADR-002-eventos-partida.md` (decisão de outbox vs. publicação
direta, com trade-offs).

### 8. Uso crítico de IA — nível: <preencher pelo grupo>
Como usamos IA e o que validamos manualmente: <descrever honestamente — por
exemplo: "usamos Claude para gerar o esqueleto do monorepo e do
partidas-service a partir do diagrama de arquitetura que o grupo já tinha
desenhado; revisamos manualmente o modelo de dados, testamos os endpoints
via docker compose e ajustamos X, Y, Z antes de aceitar">.

### 9. Execução — nível: **1 (Básico)**
Como rodar: ver seção "Como rodar" do `README.md`. `docker compose up
--build partidas-db partidas-service` sobe o serviço completo no perfil A.
Falta: script/comando único que suba todos os serviços do projeto de uma vez
quando eles existirem.

## Opcionais entregues

Nenhum ainda (grupo de 4 pessoas → CORE é o mínimo obrigatório; opcionais a
definir conforme sobrar tempo).
