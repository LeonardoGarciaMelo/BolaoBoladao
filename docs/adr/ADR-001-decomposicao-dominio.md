# ADR-001 — Decomposição de domínio em 4 microsserviços

## Status
Aceito

## Contexto
O bolão precisa lidar com 4 responsabilidades bem distintas: dados de
partidas/resultados (fonte "objetiva", vem de fora), gerenciamento de
apostas/apuração (regra de negócio central do produto), identificação de
usuários (SSO) e movimentação financeira (carteira/pagamentos, que tem
requisitos de consistência e auditoria mais rígidos que os demais).

## Decisão
Separar em 4 microsserviços com bases de dados relacionais segregadas:

1. **Partidas** — dono do ciclo de vida da partida (agendada → em andamento
   → encerrada) e do placar. Não conhece apostas nem usuários.
2. **Apostas** — dono do palpite do usuário e da apuração. Reage a eventos
   de Partidas (fim de jogo) para liquidar apostas; não acessa o banco de
   Partidas diretamente.
3. **Usuários** — dono da identidade/autenticação. Os outros serviços
   guardam apenas `user_id` (referência opaca), nunca duplicam dados
   pessoais além do necessário.
4. **Carteira/Pagamentos** — dono do saldo e do livro-razão (ledger). Reage
   a eventos de Apostas (débito ao apostar, crédito ao ganhar) e a
   depósitos/saques solicitados pelo usuário.

## Alternativas consideradas

- **Monólito modular único**: mais simples de implementar em pouco tempo de
  aula, mas não exercitaria os critérios 1–3 da rubrica (decomposição,
  comunicação assíncrona real, idempotência entre bounded contexts
  distintos). Rejeitada por não atender ao objetivo pedagógico do módulo.
- **3 serviços (fundindo Carteira em Apostas)**: reduziria a superfície do
  projeto, mas misturaria uma responsabilidade de negócio de apostas com uma
  de contabilidade/dinheiro — que tem requisitos de auditoria e consistência
  diferentes (ledger append-only, reconciliação). Rejeitada por violar
  single responsibility no nível de bounded context.
- **Split por camada técnica (ex.: um serviço "leitura" e outro "escrita")**:
  não é decomposição por domínio, geraria acoplamento forte entre os dois
  serviços por dados. Rejeitada.

## Consequências
- Cada serviço tem seu próprio Postgres — sem joins entre domínios; toda
  composição de dados entre serviços acontece via evento (Kafka) ou, no
  máximo, chamada síncrona pontual através do Gateway.
- Aumenta a complexidade operacional (4 bancos, 4 deploys) — aceitável dado
  que o objetivo do projeto é justamente exercitar esses padrões.
- Exige definição clara de contratos de evento entre os serviços (ver
  ADR-002 para o caso de Partidas).
