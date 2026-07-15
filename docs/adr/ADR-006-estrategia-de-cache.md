# ADR-006 — Estratégia de Cache e Invalidação

## Status
Aceito e implementado.

## Contexto
Tanto o serviço de Partidas quanto o de Carteira/Pagamentos lidam com um volume alto de leituras em relação às escritas. As consultas ao catálogo de partidas e aos saldos das carteiras precisam ser rápidas, ao mesmo tempo em que precisamos evitar a sobrecarga dos bancos de dados relacionais (PostgreSQL). 

## Decisão
Adotar **Redis** como camada de cache e implementar o padrão **Cache-Aside**.

- **Partidas**: O catálogo de partidas (`MatchCache.java`) faz cache das consultas e invalida ativamente (evict) as entradas quando há alteração (gols, mudança de status). Utilizamos um **TTL com Jitter** (variação aleatória no tempo de expiração) para evitar o problema de *Cache Stampede / Thundering Herd*. Além disso, evitamos fazer cache de erros 404 (ausência transitória) para impedir a propagação de inconsistências.
- **Carteira**: Utiliza Redis para armazenar em cache os saldos mais recentes.
- **Fallback**: Caso o Redis fique indisponível, o sistema recorre de forma transparente ao banco principal (PostgreSQL), garantindo a disponibilidade das leituras com algum custo de performance.

## Alternativas consideradas
- **Cache local (em memória, Caffeine/Guava)**: Mais rápido de implementar, mas dificultaria a escalabilidade horizontal e o compartilhamento de estado de invalidação entre instâncias do mesmo microsserviço. Rejeitado.
- **Não usar cache e otimizar queries**: O banco suportaria a carga inicial, mas não resolveria picos repentinos de leitura, comuns em momentos decisivos de uma partida. Rejeitado.

## Consequências
- Aumento da complexidade arquitetural ao introduzir o Redis no cluster local e nos serviços.
- Atende ao **Critério 4** (Estratégia de Cache explícita com TTL, invalidação e fallback) e melhora o desempenho das rotas públicas críticas.
