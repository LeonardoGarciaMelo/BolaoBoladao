# Linguagem do domínio

## Administrador

Usuário operacional com role `ADMIN`. O JWT de um administrador contém os grupos `USER` e `ADMIN`. Ele pode criar, iniciar, atualizar o placar, encerrar e cancelar partidas, conceder créditos e consultar auditoria. A interface não promove usuários.

## Partida

Disputa entre mandante e visitante cujo ciclo é `SCHEDULED → IN_PROGRESS → FINISHED`, ou `CANCELED`. O serviço de Partidas é a fonte de verdade do placar, dos horários e dos eventos; Apostas mantém somente uma projeção.

## Início agendado

Instante `start` informado no cadastro. Fecha a janela de palpites no limite exato e é usado pelo scheduler para iniciar a partida automaticamente.

## Duração prevista

Quantidade configurada de minutos da partida (`durationMinutes`), de 1 a 300 e padrão 105. Não representa prorrogação nem tempo efetivamente jogado.

## Término previsto

Instante `expectedEnd`. No cadastro é `start + durationMinutes`; quando o administrador antecipa o início, passa a ser `startedAt + durationMinutes`.

## Início efetivo

Instante `startedAt` em que a partida passou a `IN_PROGRESS`. No início automático conserva o início agendado; no início manual registra o instante do comando.

## Gol

Incremento de um ponto no placar do mandante ou visitante durante uma partida em andamento e antes do término previsto. O placar de cada time é limitado a 99.

## Gol anulado

Correção que reduz em um o placar de um dos times e emite um evento próprio. Nunca produz placar negativo.

## Encerramento efetivo

Instante `end` em que a partida passou a `FINISHED`. Pode ser o término previsto no fluxo automático ou o instante de um encerramento manual antecipado.

## Crédito administrativo

Crédito imutável na carteira, classificado como `ADMIN_CREDIT`. É diferente de depósito, prêmio ou bônus e sempre registra destinatário, administrador responsável, justificativa, instante, saldo anterior, valor e saldo posterior. Exige `Idempotency-Key`.

## Depósito

Entrada de saldo solicitada pelo próprio usuário. Só existe financeiramente quando a Carteira registra um lançamento imutável `DEPOSIT/CREDIT`; criar uma cobrança no provedor não altera o saldo.

## Solicitação de depósito

Registro da Carteira que coordena a preparação e confirmação de um depósito. Possui os estados `CREATING`, `PENDING`, `CONFIRMED`, `REFUSED` e `EXPIRED`, pertence a um único usuário e é idempotente pela combinação de usuário e `Idempotency-Key`.

## Cobrança PIX fictícia

Representação externa e temporária do pagamento no Boladão Pay Sandbox. Tem valor entre R$ 1,00 e R$ 10.000,00, expira em 15 minutos e termina como `PAID`, `REFUSED` ou `EXPIRED`. Ela não é saldo nem lançamento da Carteira.

## Provedor de pagamento

Sistema externo simulado que cria e consulta cobranças, hospeda o checkout e notifica a Carteira por webhook HMAC. Neste projeto é o `payment-simulator`; evitamos chamá-lo de “gateway” para não confundi-lo com o API Gateway.

## Cancelamento de partida

Transição de uma partida `SCHEDULED` ou `IN_PROGRESS` para `CANCELED`, feita por administrador com justificativa. Registra `canceledAt`, `canceledBy` e um único evento `MATCH_CANCELED`. Uma partida `FINISHED` não pode ser cancelada.

## Estorno de palpite

Crédito compensatório `BET_REFUND` vinculado ao débito do palpite. É iniciado por `BET_REFUND_REQUESTED`, concluído por `PAYMENT_REFUNDED` e idempotente pela identidade do palpite. Não é um novo depósito.

## Palpite

Registro individual feito por um usuário para uma partida, composto pelo placar exato previsto e pelo valor comprometido. **Apostar** é a ação; na interface, **aposta** não é o nome do registro. Um usuário pode fazer vários palpites para a mesma partida, inclusive palpites idênticos, desde que cada nova submissão use uma chave de idempotência diferente e haja saldo disponível.

## Janela de palpites

Período em que uma partida aceita novos palpites. Começa com a partida `SCHEDULED` e termina no horário de início previsto ou quando a partida deixa esse estado, o que ocorrer primeiro. No limite exato do horário previsto, a janela já está fechada.
