# Linguagem do domínio

## Administrador

Usuário operacional com role `ADMIN`. O JWT de um administrador contém os grupos `USER` e `ADMIN`. Ele pode criar e cancelar partidas, conceder créditos e consultar auditoria. A interface não promove usuários.

## Crédito administrativo

Crédito imutável na carteira, classificado como `ADMIN_CREDIT`. É diferente de depósito, prêmio ou bônus e sempre registra destinatário, administrador responsável, justificativa, instante, saldo anterior, valor e saldo posterior. Exige `Idempotency-Key`.

## Cancelamento de partida

Transição de uma partida `SCHEDULED` ou `IN_PROGRESS` para `CANCELED`, feita por administrador com justificativa. Registra `canceledAt`, `canceledBy` e um único evento `MATCH_CANCELED`. Uma partida `FINISHED` não pode ser cancelada.

## Estorno de palpite

Crédito compensatório `BET_REFUND` vinculado ao débito do palpite. É iniciado por `BET_REFUND_REQUESTED`, concluído por `PAYMENT_REFUNDED` e idempotente pela identidade do palpite. Não é um novo depósito.

## Palpite

Registro individual feito por um usuário para uma partida, composto pelo placar exato previsto e pelo valor comprometido. **Apostar** é a ação; na interface, **aposta** não é o nome do registro. Um usuário pode fazer vários palpites para a mesma partida, inclusive palpites idênticos, desde que cada nova submissão use uma chave de idempotência diferente e haja saldo disponível.

## Janela de palpites

Período em que uma partida aceita novos palpites. Começa com a partida `SCHEDULED` e termina no horário de início previsto ou quando a partida deixa esse estado, o que ocorrer primeiro. No limite exato do horário previsto, a janela já está fechada.
