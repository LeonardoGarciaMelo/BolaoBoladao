# ADR-004 — Saga de cancelamento e estorno

## Status

Aceito e implementado.

## Contexto

Cancelar uma partida pode exigir compensar débitos já confirmados enquanto mensagens Kafka podem chegar duplicadas ou fora da ordem entre cancelamento e confirmação do pagamento.

## Decisão

O fluxo é uma saga coreografada:

1. Partidas persiste o cancelamento e `MATCH_CANCELED` na mesma transação; o relay publica o evento com ID estável.
2. Apostas deduplica eventos, bloqueia novos palpites e move os confirmados para `REFUND_PENDING`. Palpites com pagamento pendente ficam em `CANCEL_PENDING`.
3. Apostas grava `BET_REFUND_REQUESTED` em outbox. Se `PAYMENT_ACCEPTED` chegar depois do cancelamento, o mesmo pedido é criado.
4. Carteira cria um único `BET_REFUND`, usando `bet-refund:{betId}` como chave idempotente, invalida caches e publica `PAYMENT_REFUNDED`.
5. Apostas conclui o palpite como `CANCELED` e expõe progresso agregado `PENDING`, `PROCESSING`, `COMPLETED` ou `FAILED`.
6. Falhas terminais ficam em `REFUND_FAILED`. O administrador pode reprocessá-las; Apostas volta os palpites para `REFUND_PENDING` e grava novos pedidos no outbox, preservando a idempotência financeira da Carteira.

Comandos HTTP de cancelamento também exigem `Idempotency-Key`; repetir a mesma chave retorna o cancelamento existente sem novo evento. Criação de palpite e consumo de cancelamento usam a mesma trava transacional por partida para impedir palpites confirmados após o cancelamento.

## Alternativas consideradas

- **Saga orquestrada (orchestrator central)**: um serviço coordenador gerenciaria cada passo do estorno e os retries. Ofereceria rastreamento centralizado, mas introduziria um ponto único de falha e um novo serviço para manter. Rejeitada por aumentar a complexidade operacional sem benefício proporcional para este escopo.
- **Compensação síncrona por HTTP (Apostas chama Carteira via REST)**: mais simples de raciocinar, mas acoplaria temporalmente os dois serviços — se a Carteira estivesse indisponível no instante do cancelamento, todo o fluxo falharia. Rejeitada por fragilidade e por violar a comunicação assíncrona exigida no critério 2.
- **Não compensar e apenas bloquear novos palpites**: evitaria toda a complexidade de estorno, mas deixaria dinheiro preso na Carteira sem devolução, o que seria inaceitável em um domínio financeiro. Rejeitada por violar a integridade do saldo do usuário.

## Consequências

O sistema aceita consistência eventual e exibe o progresso em vez de declarar sucesso prematuro. Falhas permanecem visíveis e reprocessáveis. O armazenamento de deduplicação e outbox cresce e precisará de retenção operacional futura.
