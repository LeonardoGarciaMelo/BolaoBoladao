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

Hoje, cada transição de partida (`iniciar`, `gol`, `encerrar`) já grava um
registro em `match_event` na mesma transação que altera o `match` — essa
tabela é a fonte de verdade interna e append-only.

## Decisão (proposta)
Publicar no tópico Kafka `match-events` a partir da tabela `match_event`,
usando o padrão **transactional outbox**: a escrita em `match_event` e o
"agendamento" da publicação acontecem na mesma transação de banco; um
processo separado (poller ou Debezium/CDC) lê os eventos ainda não
publicados e envia ao Kafka, marcando como publicados depois do ack.

Cada evento carrega um `event_id` estável (o próprio id do `match_event`),
permitindo que o consumidor (Apostas) implemente idempotência por chave.

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
- Este ADR deve ser atualizado para "Aceito" quando a implementação entrar,
  com o caminho do código como evidência (critério 7 da rubrica).
