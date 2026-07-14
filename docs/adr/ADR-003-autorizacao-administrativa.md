# ADR-003 — Autorização administrativa e bootstrap

## Status

Aceito e implementado.

## Contexto

Operações de partida e carteira têm impacto financeiro. Confiar apenas em rota escondida, cabeçalho do cliente ou validação exclusiva no gateway deixaria os serviços internos vulneráveis a chamadas laterais.

## Decisão

- Usuários persiste `USER` ou `ADMIN`; administradores recebem `USER` e `ADMIN` nos grupos do JWT RS256.
- O gateway valida assinatura, algoritmo, issuer, audience e expiração e protege `/api/admin/**` com `ADMIN`.
- Partidas, Carteira, Apostas e Usuários validam o mesmo JWT e a mesma role novamente.
- Cabeçalhos de identidade enviados pelo cliente são descartados; o gateway deriva `X-Authenticated-User-Id` do `sub`.
- A primeira conta administrativa é criada no startup por nome, username e senha montada como secret. Reinícios são idempotentes, a senha existente não é alterada e colisão com usuário comum impede o startup.

## Consequências

Há defesa em profundidade e um caminho reproduzível para iniciar a operação sem endpoint de promoção. Rotação de secrets e promoção/revogação de administradores ficam fora deste incremento.
