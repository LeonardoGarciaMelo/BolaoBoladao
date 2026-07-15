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
implementados. A apuração e o pagamento de prêmios continuam fora do escopo.

## Serviços

| Serviço | Status | Stack | Porta |
|---|---|---|---|
| `partidas-service` | ✅ implementado | Quarkus 3 (Java 21) + PostgreSQL + Flyway | interna (8081) |
| `user-service` | ✅ implementado | Quarkus 3 (Java 21) + PostgreSQL + Flyway | interna (8082) |
| `api-gateway` | ✅ implementado | Quarkus 3 (Java 21) + JWT RS256 | pública (8080) |
| `web` | ✅ implementado | Astro + Nginx | interna (80) |
| `carteira-service` | ✅ implementado | Quarkus 3 (Java 21) + PostgreSQL + Redis + Kafka | interna (8080) |
| `apostas-service` | ✅ implementado | FastAPI (Python 3.12) + PostgreSQL + Kafka | interna (8000) |

## Perfis de execução

O projeto roda em 3 perfis, conforme o ambiente disponível (ver §2 do
enunciado — princípio anti-ambiente: a nota avalia a decisão, não a infra):

- **Perfil A — tudo em Docker** (recomendado): banco sobe via
  `docker-compose.yml`, serviço também containerizado.
- **Perfil B — restrito/JVM**: sem Docker; aponta para um Postgres já
  provisionado externamente (local ou compartilhado pela equipe).
- **Perfil C — conceitual**: sem execução real; apenas compila e roda os
  testes puramente unitários (sem subir banco).

**Perfil usado nesta entrega do `partidas-service`: A**, com fallback para B
documentado abaixo.

## Como rodar a plataforma completa

Gere um par RSA somente para desenvolvimento. A chave privada não deve ser
versionada nem compartilhada fora do ambiente local.

```bash
mkdir -p .secrets
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out .secrets/jwt-private.pem
openssl pkey -in .secrets/jwt-private.pem -pubout -out .secrets/jwt-public.pem
openssl rand -base64 24 > .secrets/admin-password
docker compose up --build
```

A web e a API estarão em `http://localhost:8080`: o gateway entrega a
interface e encaminha as chamadas sob `/api`. Nenhum backend HTTP além do
gateway publica porta no host quando executado pelo Compose.

Este incremento altera as tabelas locais de Apostas e as projeções derivadas
de eventos. Não há backfill dos dados anteriores. Em ambientes de
desenvolvimento, recrie os volumes antes da primeira subida desta versão:

```bash
docker compose down -v
docker compose up --build
```

O fluxo é cadastro (`POST /api/auth/register`) → login
(`POST /api/auth/login`) → Bearer JWT nas chamadas protegidas, como
`GET /api/partidas`. O gateway valida assinatura RS256, issuer, audience e
expiração antes de encaminhar a chamada. Access tokens duram uma hora e não há
refresh token nesta versão.

O Compose cria o primeiro administrador a partir de `.secrets/admin-password`
e das variáveis `ADMIN_BOOTSTRAP_NAME` e `ADMIN_BOOTSTRAP_USERNAME`. A criação
é idempotente: reinícios não trocam a senha e uma colisão com usuário comum
impede o startup. Após o login, `GET /api/auth/me` retorna identidade e roles;
`ADMIN` segue para `/admin` e `USER` para `/partidas`.

A carteira também é acessada exclusivamente com Bearer JWT pelo gateway. O
painel usa `GET /api/wallet/me` e
`GET /api/wallet/me/statement?page=0&size=10`, sem aceitar identidade ou
`walletId` do cliente. As rotas administrativas e legadas permanecem
compatíveis.

## Painel do usuário

Após o login, usuários comuns seguem para `/partidas`. O shell autenticado é
compartilhado por `/partidas`, `/palpites`, `/carteira` e `/perfil`; exibe saldo,
atalho de adição de saldo (somente diálogo “Em breve”) e menu da conta.

| Método | Rota | Descrição |
|---|---|---|
| `GET` | `/api/partidas/catalog?view=OPEN&page=0&size=12` | Catálogo ordenado; views `OPEN`, `LIVE`, `FINISHED` e `CANCELED` |
| `POST` | `/api/bets` | Cria palpite; exige `Idempotency-Key` e valor mínimo de R$ 1,00 |
| `GET` | `/api/bets?status=&page=0&size=10` | Lista paginada dos palpites do usuário autenticado |
| `GET` | `/api/bets/{id}` | Consulta um palpite do usuário autenticado |
| `GET` | `/api/wallet/me` | Obtém ou cria a carteira e retorna saldo em centavos |
| `GET` | `/api/wallet/me/statement?page=0&size=10` | Extrato paginado do usuário autenticado |

Vários palpites para a mesma partida, inclusive idênticos, são permitidos. A
janela fecha no início previsto ou quando a partida deixa `SCHEDULED`, o que
ocorrer primeiro. O débito continua assíncrono e serializado pela Carteira;
por isso uma segunda submissão concorrente pode ser recusada após o saldo ter
sido consumido pela primeira.

## Como rodar — `partidas-service` isoladamente

### Perfil A (Docker)

Use a plataforma completa descrita acima. Pelo Compose, Partidas não publica
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
docker compose --profile test run --rm apostas-tests
docker compose --profile test run --rm web-e2e
```

O Playwright usa `http://api-gateway:8080`, nunca um backend diretamente.

## Endpoints administrativos — gateway

| Método | Rota | Descrição |
|---|---|---|
| `GET` | `/api/admin/users` | Busca paginada por nome/username |
| `GET` | `/api/admin/teams` | Autocomplete de times |
| `GET/POST` | `/api/admin/partidas` | Lista e cria partidas |
| `POST` | `/api/admin/partidas/{id}/cancel` | Cancela com justificativa e `Idempotency-Key` |
| `GET` | `/api/admin/partidas/{id}/refunds` | Progresso dos estornos |
| `POST` | `/api/admin/partidas/{id}/refunds/retry` | Reprocessa estornos que terminaram em `FAILED` |
| `GET` | `/api/admin/wallets/users/{userId}` | Carteira e saldo do usuário |
| `POST` | `/api/admin/wallets/users/{userId}/credits` | Crédito administrativo em centavos |
| `GET` | `/api/admin/activity` | Linha do tempo composta com cursor opaco e snapshot estável |

As mutações antigas de `/api/partidas/**` foram removidas; essa família fica
somente para leitura autenticada.

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
└── pom.xml                  # parent, multi-módulo, profiles A/B/C
```
