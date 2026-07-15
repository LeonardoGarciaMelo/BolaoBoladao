# Projeto Final em Grupo — BE-JV-010 (Nível III)

## 1. Princípio
O tema é livre, os critérios são a amarra. O projeto precisa exercitar os padrões do módulo de forma justificada. A API de Comprovantes PIX é apenas uma sugestão de referência (transferir os padrões para um problema próprio é o objetivo).

**Sugestões de tema:**
*   **Central de cobrança / boletos** — emissão → fila de registro → cache de consulta → tópico de baixa/liquidação.
*   **Antifraude em streaming** — transações em tópico → análise → cache de score → fila de bloqueio (Kafka-pesado).
*   **Consolidador de extrato / Open Finance** — ingestão por tópicos → agregação → cache de consulta.
*   Qualquer domínio do dia a dia do grupo na Caixa que justifique os padrões — preferível, pois aproxima do trabalho real.

## 2. Escopo escalável
O esforço acompanha o tamanho do grupo. CORE é obrigatório; cada integrante acima de 3 assume 1 item opcional.

### CORE (todo grupo entrega)
*   **≥ 2 microsserviços** com **bases segregadas** (decomposição por domínio).
*   **1 fluxo assíncrono** com fila (producer/consumer) e **consumidor idempotente**.
*   **1 cache** com estratégia e invalidação explícitas.
*   **1 contrato executável** (contract test) entre dois serviços.
*   **ADRs** documentando as decisões + **README** de arquitetura.
*   **Roda** em pelo menos um perfil (A/B/C) — declarado.

### Opcionais (+1 por integrante acima de 3)
*   **SAGA** de orquestração ou coreografia com compensação.
*   **Tópico Kafka** com múltiplos consumer groups.
*   **Resiliência avançada** (`@RetryableTopic` + circuit breaker + DLQ).
*   **2º par de contract test** / verificação estilo eval.
*   **Observabilidade básica** (logs estruturados + correlação de id entre serviços).
*   **Uso documentado e crítico de IA** no design/desenvolvimento (vira evidência do critério 8).

**Perfis de Execução (Grupos declaram um no README):**
*   **A:** em docker
*   **B:** restrito/JVM
*   **C:** conceitual (Princípio anti-ambiente: avaliam domínio do padrão e qualidade da decisão. Se rodou no C e justificou bem, pode tirar nota máxima).

## 3. Contrato de entrega (estrutura padronizada do repositório)

**Obrigatória Repositório ou zip com:**
```text
<projeto-do-grupo>/
├── README.md                # tema, problema, visão de arquitetura, como rodar (perfil A/B/C)
├── AVALIACAO.md             # auto-avaliação: cada critério → evidência (arquivo/commit)
├── docs/
│   ├── adr/                 # 1 arquivo por decisão (ADR-001-..., formato curto)
│   └── arquitetura.md       # diagrama de contextos + fluxo de eventos/filas
├── <servico-1>/ ... <servico-N>/ # um módulo por microsserviço, base segregada
├── shared-contracts/        # DTOs/eventos/pacts compartilhados
└── pom.xml                  # multi-módulo, profiles A/B/C
```

**Regras que tornam a entrega avaliável:**
*   `AVALIACAO.md` é obrigatório e mapeia cada critério → evidência (caminho de arquivo, classe, teste ou commit). Sem ele, a nota não considera o que não for encontrado.
*   Declarar no README o perfil de execução (A/B/C) em que rodou e os fallbacks usados. Não há penalização por restrição de ambiente.
*   Commits ao longo das semanas (não um único dump) — evidência de processo.

## 4. Critérios de avaliação (rubrica)

Pesos somam 100. Cada critério tem 4 níveis: 0 Insuficiente · 1 Básico · 2 Proficiente · 3 Avançado.
Nota do critério = (nível/3) × peso.

| # | Critério | Peso | Evidência esperada |
| :--- | :--- | :--- | :--- |
| 1 | **Decomposição de domínio** (bounded contexts, bases segregadas, sem acoplamento por dados) | 15 | módulos por serviço; bases separadas; `docs/arquitetura.md`; ADR de corte |
| 2 | **Comunicação assíncrona** (producer/consumer, mensagem×evento, garantia de entrega) | 15 | config de fila/broker; consumidor; README declara a garantia |
| 3 | **Idempotência e consistência** (consumidor idempotente; SAGA/compensação/outbox quando aplicável) | 12 | chave de idempotência; teste que reprocessa sem duplicar; ADR |
| 4 | **Cache** (estratégia, TTL, invalidação, fallback) | 10 | camada de cache; lógica de invalidação; ADR da estratégia |
| 5 | **Resiliência** (retry c/ backoff, DLQ, timeout, circuit breaker) | 12 | config de retry/DLQ/breaker; teste de falha transitória × permanente |
| 6 | **Testabilidade** (contract test executável; testes rodam em perfil sem Docker) | 13 | pact files + verificação; `mvn verify` verde; testes de integração pura-JVM |
| 7 | **Decisões arquiteturais** (ADRs com trade-offs reais, não descrição) | 13 | `docs/adr/` com alternativas consideradas e justificativa |
| 8 | **Uso crítico de IA** (como usaram IA no design/dev; o que validaram à mão) | 5 | seção no README/AVALIACAO; reflexão honesta, não marketing |
| 9 | **Execução comprovada** (roda no perfil declarado; instruções reproduzíveis) | 5 | README "como rodar"; perfil A/B/C declarado; evidência de execução |

## 5. AVALIACAO.md — template que o grupo preenche

```markdown
# Auto-avaliação — <nome do projeto>
Grupo: <integrantes e papéis>
Tema/domínio: <descrição em 2 linhas>
Perfil de execução: A | B | C · Fallbacks usados: <quais>

## Evidências por critério
1. Decomposição de domínio — nível auto-atribuído: X
Evidência: <serviços/módulos, bases, docs/arquitetura.md, ADR-00X>
2. Comunicação assíncrona — X
Evidência: <arquivos, garantia de entrega declarada>
3. Idempotência e consistência — X
Evidência: <chave, teste, ADR>
4. Cache — X / 5. Resiliência — X / 6. Testabilidade — X
Evidência: <...>
7. Decisões arquiteturais — X → ver docs/adr/
8. Uso crítico de IA — X
Como usamos IA e o que validamos manualmente: <texto honesto>
9. Execução — X
Como rodar: <comandos>; perfil declarado.

## Opcionais entregues
<lista, se grupo > 3 pessoas>
```
