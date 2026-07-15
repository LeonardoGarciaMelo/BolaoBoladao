# Bolão Boladão

Projeto Final em Grupo — BE-JV-010 (Nível III).

## Tema / problema

Plataforma de bolão de apostas em partidas de futebol. Usuários se cadastram,
consultam partidas e seus resultados, fazem palpites (apostas) sobre o placar
e movimentam uma carteira digital para pagar apostas e receber prêmios.

## Visão de arquitetura

4 domínios / microsserviços, cada um com base de dados relacional própria
(sem acoplamento por dados), comunicando-se de forma assíncrona via Kafka
e expostos através de um API Gateway único para os clientes Web/Mobile.
**Garantia de entrega:** O projeto utiliza o padrão *Transactional Outbox* aliado a consumidores idempotentes, garantindo entrega assíncrona *at-least-once* sem processamento duplicado acidental, reforçando a consistência entre os domínios.

```
Web / Mobile
     │
     ▼
API Gateway
     │
     ├── Partidas        (partidas, times, resultados)         → Postgres próprio
     ├── Apostas          (palpites, apuração, ranking)          → Postgres próprio
     ├── Usuários         (SSO, identificação)                   → Postgres próprio
     └── Carteira/Pagamentos (wallet, ledger, saldo diário)       → Postgres próprio

                    Kafka (eventos entre os serviços)
```

Diagrama completo e catálogo de eventos: [`docs/arquitetura.md`](docs/arquitetura.md).

**Status atual:** Partidas, Usuários, Carteira/Pagamentos, Apostas, API Gateway,
o painel administrativo, o painel do usuário e a integração via Kafka estão
implementados. A apuração (com rateio proporcional de bolão) e o pagamento automatizado de prêmios estão 100% funcionais!

Na apuração, somente palpites que acertam o placar final exato são vencedores. O
bolão formado pelos valores de todos os palpites confirmados é dividido entre
esses vencedores proporcionalmente ao valor de cada palpite, sem bônus ou prêmio
mínimo. Se ninguém acertar exatamente, todos os palpites perdem e nenhum prêmio
é creditado. O rateio opera em centavos e distribui eventuais resíduos pelas
maiores frações, garantindo que a soma dos prêmios seja igual ao bolão.

## Serviços

| Serviço | Status | Stack | Porta |
|---|---|---|---|
| `partidas-service` | ✅ implementado | Quarkus 3 (Java 21) + PostgreSQL + Flyway | interna (8081) |
| `user-service` | ✅ implementado | Quarkus 3 (Java 21) + PostgreSQL + Flyway | interna (8082) |
| `api-gateway` | ✅ implementado | Quarkus 3 (Java 21) + JWT RS256 | pública (8080) |
| `web` | ✅ implementado | Astro + Nginx | interna (80) |
| `carteira-service` | ✅ implementado | Quarkus 3 (Java 21) + PostgreSQL + Redis + Kafka | interna (8080) |
| `apostas-service` | ✅ implementado | FastAPI (Python 3.12) + PostgreSQL + Kafka | interna (8000) |
| `payment-simulator` | ✅ implementado | Elixir 1.20.1/OTP 28 + Phoenix 1.8.9 + Oban + PostgreSQL | local (127.0.0.1:4000) |

## Perfis de execução

O projeto foi consolidado para rodar exclusivamente no **Perfil A — tudo em Docker**. A nota avalia a decisão arquitetural, e a escolha por containerização total simplifica a infraestrutura e garante reprodutibilidade. 

O banco de dados, brokers (Kafka), serviços, simuladores e interface sobem inteiramente via `docker-compose.yml`, não sendo necessário gerenciar a JVM, serviços externos ou ambientes localmente instalados.

## Como rodar a plataforma completa

O projeto possui um serviço de inicialização (`init-secrets`) que gera automaticamente as chaves RSA e senhas temporárias de desenvolvimento no primeiro uso, sem que você precise expor credenciais no repositório.

Basta rodar:

```bash
docker compose up --build
```

A web e a API estarão em `http://localhost:8080`: o gateway entrega a
interface e encaminha as chamadas sob `/api`. Nenhum dos quatro contextos
centrais publica porta HTTP no host além do gateway; o simulador externo é a
exceção local e fica restrito a `127.0.0.1:4000`.

As alterações de ciclo de vida e depósitos usam migrações incrementais. Não é
necessário apagar ou recriar volumes; o Flyway preenche duração e horários das
partidas existentes ao subir a nova versão.

O fluxo é cadastro (`POST /api/auth/register`) → login
(`POST /api/auth/login`) → Bearer JWT nas chamadas protegidas, como
`GET /api/partidas`. O gateway valida assinatura RS256, issuer, audience e
expiração antes de encaminhar a chamada. Access tokens duram uma hora e não há
refresh token nesta versão.

O Compose cria o primeiro administrador a partir do script `init-secrets`. 

> [!IMPORTANT]  
> **Para fins de avaliação e apresentação, o usuário administrador padrão é criado com as seguintes credenciais fixas:**
> - **Login:** `admin`
> - **Senha:** `admin-boladao`
> 
> *Nota: Essa configuração destina-se estritamente à demonstração local. Em produção, as senhas seriam injetadas via Secrets Manager.*

A criação é idempotente: reinícios não trocam a senha e uma colisão com usuário comum impede o startup. Após o login, `GET /api/auth/me` retorna identidade e roles;
`ADMIN` segue para `/admin` e `USER` para `/partidas`.

A carteira também é acessada exclusivamente com Bearer JWT pelo gateway. O
painel usa `GET /api/wallet/me` e
`GET /api/wallet/me/statement?page=0&size=10`, sem aceitar identidade ou
`walletId` do cliente. As rotas administrativas e legadas permanecem
compatíveis.

### Inspecionar os bancos com pgAdmin

O perfil opcional `tools` disponibiliza o pgAdmin somente na interface local:

```bash
docker compose --profile tools up -d pgadmin
```

Acesse `http://localhost:5050` com `admin@local.dev` / `admin`. O login pode
ser sobrescrito com `PGADMIN_DEFAULT_EMAIL` e `PGADMIN_DEFAULT_PASSWORD`.
Cadastre os servidores na porta `5432`, usando usuário e senha `bolao`:

| Serviço | Host | Banco |
|---|---|---|
| Partidas | `partidas-db` | `partidas_db` |
| Carteira | `carteira-db` | `carteira_db` |
| Apostas | `apostas-db` | `apostas_db` |
| Usuários | `user-db` | `user_db` |
| Boladão Pay Sandbox | `payment-simulator-db` | `payment_simulator_db` |

O volume `pgadmin_data` preserva os servidores cadastrados entre reinícios.

### Inspecionar o Kafka com Kafbat UI

O mesmo perfil `tools` disponibiliza o Kafbat UI, já configurado para o cluster
Kafka local:

```bash
docker compose --profile tools up -d kafbat-ui
```

Acesse `http://localhost:5051`. O cluster aparece como `bolao-local` e permite
inspecionar tópicos, mensagens, partições e consumer groups. A porta é publicada
somente na interface local; a ferramenta possui permissões administrativas e é
destinada apenas ao ambiente de desenvolvimento.

Para iniciar as duas ferramentas de uma vez:

```bash
docker compose --profile tools up -d pgadmin kafbat-ui
```

## Painel do usuário

Após o login, usuários comuns seguem para `/partidas`. O shell autenticado é
compartilhado por `/partidas`, `/palpites`, `/carteira` e `/perfil`; exibe saldo,
abre o depósito PIX fictício em duas etapas e mantém o menu da conta. O checkout
standalone fica em `/pagamento` e deixa explícito que nenhuma cobrança é real.

| Método | Rota | Descrição |
|---|---|---|
| `GET` | `/api/partidas/catalog?view=OPEN&page=0&size=12` | Catálogo ordenado; views `OPEN`, `LIVE`, `FINISHED` e `CANCELED` |
| `POST` | `/api/bets` | Cria palpite; exige `Idempotency-Key` e valor mínimo de R$ 1,00 |
| `GET` | `/api/bets?status=&page=0&size=10` | Lista paginada dos palpites do usuário autenticado |
| `GET` | `/api/bets/{id}` | Consulta um palpite do usuário autenticado |
| `GET` | `/api/wallet/me` | Obtém ou cria a carteira e retorna saldo em centavos |
| `GET` | `/api/wallet/me/statement?page=0&size=10` | Extrato paginado do usuário autenticado |
| `POST` | `/api/wallet/me/deposits` | Cria ou retoma depósito; exige `Idempotency-Key` |
| `GET` | `/api/wallet/me/deposits?page=0&size=10` | Lista solicitações de depósito do proprietário |
| `GET` | `/api/wallet/me/deposits/{id}` | Consulta depósito sem revelar registros de outro usuário |
| `POST` | `/api/wallet/me/deposits/{id}/reconcile` | Consulta o provedor e reaplica o resultado idempotentemente |

Vários palpites para a mesma partida, inclusive idênticos, são permitidos. A
janela fecha no início previsto ou quando a partida deixa `SCHEDULED`, o que
ocorrer primeiro. O débito continua assíncrono e serializado pela Carteira;
por isso uma segunda submissão concorrente pode ser recusada após o saldo ter
sido consumido pela primeira.

## Como rodar — microsserviços isoladamente (Ex: `partidas-service`)

### Perfil A (Docker)

Use a plataforma completa descrita acima. Pelo Compose, os microsserviços internos (como Partidas) não publicam
porta no host: a chamada autenticada é feita em
`http://localhost:8080/api/partidas` pelo gateway.

### Perfil B (JVM, sem Docker)

Requer PostgreSQL 16 rodando localmente (ou acessível na rede), com um banco
`partidas_db` e um usuário com permissão de criar tabelas.

```bash
export PARTIDAS_DB_URL=jdbc:postgresql://localhost:5432/partidas_db
export PARTIDAS_DB_USER=bolao
export PARTIDAS_DB_PASSWORD=bolao

cd partidas-service
mvn -pl . -am -Pquarkus quarkus:dev
```

O Flyway cria o schema automaticamente na primeira subida.

### Perfil C (conceitual)

```bash
mvn -pl partidas-service -am -PC test -Dtest='!*ResourceTest'
```

(roda apenas testes que não exigem `@QuarkusTest`/Testcontainers).

### Testes automatizados pelo Compose

```bash
docker compose --profile test run --rm backend-tests
docker compose --profile test run --build --rm apostas-tests
docker compose --profile test run --build --rm payment-simulator-tests
docker compose --profile test run --rm web-e2e
```

O Playwright usa `http://api-gateway:8080`, nunca um backend diretamente.
Os testes Java publicam eventos de partida somente em `match-events-test`,
mantendo o tópico de desenvolvimento `match-events` livre de dados da suíte.

## Endpoints administrativos — gateway

| Método | Rota | Descrição |
|---|---|---|
| `GET` | `/api/admin/users` | Busca paginada por nome/username |
| `GET` | `/api/admin/teams` | Autocomplete de times |
| `GET/POST` | `/api/admin/partidas` | Lista e cria partidas |
| `POST` | `/api/admin/partidas/{id}/iniciar` | Antecipa o início da partida |
| `POST` | `/api/admin/partidas/{id}/gol` | Marca gol de `HOME` ou `AWAY` |
| `POST` | `/api/admin/partidas/{id}/gol/anular` | Anula gol de `HOME` ou `AWAY` |
| `POST` | `/api/admin/partidas/{id}/encerrar` | Antecipa o encerramento da partida |
| `POST` | `/api/admin/partidas/{id}/cancel` | Cancela com justificativa e `Idempotency-Key` |
| `GET` | `/api/admin/partidas/{id}/refunds` | Progresso dos estornos |
| `POST` | `/api/admin/partidas/{id}/refunds/retry` | Reprocessa estornos que terminaram em `FAILED` |
| `GET` | `/api/admin/wallets/users/{userId}` | Carteira e saldo do usuário |
| `POST` | `/api/admin/wallets/users/{userId}/credits` | Crédito administrativo em centavos |
| `GET` | `/api/admin/activity` | Linha do tempo composta com cursor opaco e snapshot estável |

As mutações antigas de `/api/partidas/**` foram removidas; essa família fica
somente para leitura autenticada.

Criação e todas as mutações administrativas de partida exigem
`Idempotency-Key`. A duração aceita 1–300 minutos (padrão 105). O serviço inicia
e encerra partidas automaticamente a cada segundo; os painéis visíveis se
atualizam a cada cinco segundos.

## Uso Crítico de IA (Design e Desenvolvimento)

A Inteligência Artificial foi adotada como um *peer partner* durante todo o ciclo de vida deste projeto:
- **Design de Arquitetura:** A IA auxiliou na modelagem dos limites de contexto (Bounded Contexts) e na elaboração da estratégia robusta para a mensageria, como a sugestão do padrão *Transactional Outbox* para garantir o `at-least-once` sem perdas.
- **Desenvolvimento:** Acelerou a implementação de boilerplate de Quarkus/FastAPI, geração de testes de integração, manipulação dos fluxos reativos e configuração da infraestrutura docker (PostgreSQL, Kafka KRaft, Redis, pgAdmin, Kafbat UI).
- **Validação Manual:** A IA não tomou decisões sozinha. Todo o código gerado foi validado através de testes de integração (DevServices), testes manuais simulando quedas de broker e banco de dados, e conferência estrita de todos os scripts DDL de migração (Flyway) para assegurar a criação e manutenção correta do modelo relacional.

## Estrutura do repositório

```
bolao-boladao/
├── README.md
├── AVALIACAO.md
├── docker-compose.yml
├── docs/
│   ├── adr/
│   └── arquitetura.md
├── shared-contracts/        # DTOs/eventos compartilhados entre serviços
├── partidas-service/        # ✅ implementado
├── user-service/            # ✅ implementado
├── api-gateway/             # ✅ implementado (Quarkus + JWT)
├── web/                     # ✅ implementado
├── apostas-service/         # ✅ versão inicial (FastAPI)
├── carteira-service/        # ✅ implementado
├── payment-simulator/       # ✅ provedor PIX fictício externo (Phoenix/Oban)
└── pom.xml                  # parent, multi-módulo, profiles A/B/C
```
