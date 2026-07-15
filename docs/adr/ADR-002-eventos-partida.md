# ADR-002 — Publicação de eventos de partida

## Status
Aceito - Implementado no partidas-service.

## Contexto
O serviço de Apostas precisa saber quando uma partida começa (para travar
novos palpites, se essa regra existir) e, principalmente, quando ela termina
com o placar final (para disparar a apuração/liquidação das apostas). Essa
comunicação precisa ser assíncrona (critério 2 da rubrica) e não pode
perder eventos nem duplicá-los de forma que quebre a apuração
(critério 3 — idempotência).

Hoje, cada transição de partida (`criar`, `iniciar`, `gol`, `anular gol`, `encerrar`, `cancelar`) grava um
registro em `match_event` na mesma transação que altera o `match` — essa
tabela é a fonte de verdade interna e append-only.

## Decisão
Publicar no tópico Kafka `match-events` a partir da tabela `match_event`,
usando o padrão **transactional outbox**: a escrita em `match_event` e o
"agendamento" da publicação acontecem na mesma transação de banco; um
processo separado (poller ou Debezium/CDC) lê os eventos ainda não
publicados e envia ao Kafka, marcando como publicados depois do ack.

Cada evento carrega um `event_id` globalmente único e estável no formato
`<matchId>:<sequenceId>`. O `sequenceId` continua sendo o id local do
`match_event`, enquanto o `matchId` evita colisões entre bancos, produtores ou
ambientes que iniciem suas sequências no mesmo valor.

O consumidor de Apostas aceita também o contrato legado, no qual `event_id`
era numérico. Nesse caso, a deduplicação combina tópico, `match_id` e
`event_id`: uma reentrega da mesma partida permanece idempotente, mas eventos
de partidas diferentes com a mesma sequência local são processados.

## Alternativas consideradas
- **Publicar direto no Kafka dentro do mesmo método de serviço, após o
  commit**: mais simples de implementar, mas se o processo cair entre o
  commit do banco e o `send()` do Kafka, o evento se perde — sem garantia
  "at-least-once" de fato. Adequado para uma entrega mínima, mas fraco no
  critério de garantia de entrega.
- **Publicar antes do commit do banco**: arriscado no sentido oposto —
  pode publicar um evento de algo que acaba não sendo persistido (rollback).
  Rejeitada.
- **Transactional outbox (escolhida)**: garante que o evento só é
  considerado "pendente de publicação" se a transação de negócio realmente
  commitou, e o poller garante retry até publicar. Mais trabalho de
  implementação, mas é o padrão correto para o critério de garantia de
  entrega da rubrica.

## Consequências
- Precisa de uma tabela/flag extra de controle de publicação (ou usar a
  própria `match_event` com uma coluna `published_at`).
- Precisa de um worker (scheduled job do Quarkus, `@Scheduled`) fazendo o
  polling — soma complexidade, mas evita depender de Debezium/CDC para o
  escopo deste projeto.
- O relay está implementado em `MatchOutboxRelay`; `match_event.published`
  controla a confirmação e o lote para no primeiro erro para preservar ordem.
- O consumidor de Apostas inicia grupos novos com `auto.offset.reset=earliest`,
  incluindo eventos publicados antes de sua primeira conexão.
- Testes Java publicam em `match-events-test`; o tópico de desenvolvimento
  `match-events` não recebe eventos gerados pela suíte.
- `MATCH_CANCELED` inclui administrador e justificativa e inicia a saga descrita
  no ADR-004.
- O ciclo temporal é avançado por um poller PostgreSQL a cada segundo. Cada
  partida é revalidada sob lock pessimista e em transação própria. Em catch-up,
  `MATCH_STARTED` é confirmado antes de `MATCH_ENDED`; `occurred_at` registra
  quando a recuperação foi processada.
- Criação e comandos administrativos usam `Idempotency-Key`. Chave e impressão
  SHA-256 ficam no `match_event`: replay idêntico não cria evento e reutilização
  com outro conteúdo retorna `409`.
- Duração, término previsto, início efetivo e encerramento efetivo são snapshots
  do instante do evento no `match_event`; um relay atrasado não publica estado
  futuro em eventos antigos.
