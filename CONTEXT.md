# Linguagem do domínio

## Administrador

Usuário operacional com role `ADMIN`. O JWT de um administrador contém os grupos `USER` e `ADMIN`. Ele pode criar e cancelar partidas, conceder créditos e consultar auditoria. A interface não promove usuários.

## Crédito administrativo

Crédito imutável na carteira, classificado como `ADMIN_CREDIT`. É diferente de depósito, prêmio ou bônus e sempre registra destinatário, administrador responsável, justificativa, instante, saldo anterior, valor e saldo posterior. Exige `Idempotency-Key`.

## Cancelamento de partida

Transição de uma partida `SCHEDULED` ou `IN_PROGRESS` para `CANCELED`, feita por administrador com justificativa. Registra `canceledAt`, `canceledBy` e um único evento `MATCH_CANCELED`. Uma partida `FINISHED` não pode ser cancelada.

## Estorno de palpite

Crédito compensatório `BET_REFUND` vinculado ao débito do palpite. É iniciado por `BET_REFUND_REQUESTED`, concluído por `PAYMENT_REFUNDED` e idempotente pela identidade do palpite. Não é um novo depósito.
