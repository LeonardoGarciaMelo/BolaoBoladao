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

## Alternativas consideradas

- **Autorização apenas no gateway (sem revalidação nos serviços)**: mais simples, mas deixaria Partidas, Carteira e Apostas vulneráveis se a rede interna fosse acessível diretamente (port-forwarding acidental, teste manual, etc.). Rejeitada por violar defesa em profundidade.
- **Cabeçalho `X-Admin: true` enviado pelo cliente e confiado pelo backend**: trivial de implementar, mas qualquer chamada lateral forjaria a role sem verificação criptográfica. Rejeitada por segurança insuficiente.
- **Endpoint de promoção de usuário para ADMIN** (ex.: `POST /admin/promote`): evitaria o bootstrap por secret, mas criaria um problema de ovo-e-galinha (quem promove o primeiro admin?) e ampliaria a superfície de ataque. Rejeitada em favor da criação idempotente no startup com secret montado.

## Consequências

Há defesa em profundidade e um caminho reproduzível para iniciar a operação sem endpoint de promoção. Rotação de secrets e promoção/revogação de administradores ficam fora deste incremento.
