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

**Status atual:** Partidas, Usuários e o API Gateway estão implementados.
Apostas, Carteira/Pagamentos e a integração via Kafka permanecem para os
próximos incrementos.

## Serviços

| Serviço | Status | Stack | Porta |
|---|---|---|---|
| `partidas-service` | ✅ implementado | Quarkus 3 (Java 21) + PostgreSQL + Flyway | 8081 |
| `user-service` | ✅ implementado | Quarkus 3 (Java 21) + PostgreSQL + Flyway | 8082 |
| `api-gateway` | ✅ implementado | Quarkus 3 (Java 21) + JWT RS256 | 8080 |
| `web` | ✅ implementado | Astro + Nginx | 4321 |
| `apostas-service` | ⏳ pendente | — | — |
| `usuarios-service` | ⏳ pendente | — | — |
| `carteira-service` | ⏳ pendente | — | — |

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
docker compose up --build
```

A web estará em `http://localhost:4321` e a única API pública em
`http://localhost:8080`. Os serviços de Partidas e Usuários não expõem portas
no host quando executados pelo Compose.

O fluxo é cadastro (`POST /api/auth/register`) → login
(`POST /api/auth/login`) → Bearer JWT nas chamadas protegidas, como
`GET /api/partidas`. O gateway valida assinatura RS256, issuer, audience e
expiração antes de encaminhar a chamada. Access tokens duram uma hora e não há
refresh token nesta versão.

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

### Testes de integração (Testcontainers — exige Docker)

```bash
mvn -pl partidas-service -am verify
```

## Endpoints — `partidas-service`

| Método | Rota | Descrição |
|---|---|---|
| `POST` | `/partidas` | Cria uma partida (cria os times se não existirem) |
| `GET` | `/partidas` | Lista todas as partidas |
| `GET` | `/partidas/{id}` | Detalhe de uma partida |
| `GET` | `/partidas/{id}/eventos` | Histórico de eventos da partida |
| `POST` | `/partidas/{id}/iniciar` | Inicia a partida (`SCHEDULED` → `IN_PROGRESS`) |
| `POST` | `/partidas/{id}/gol` | Registra gol (`{"side": "HOME"\|"AWAY"}`) |
| `POST` | `/partidas/{id}/encerrar` | Encerra a partida (`IN_PROGRESS` → `FINISHED`) |

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
├── apostas-service/         # ⏳ pendente
├── usuarios-service/        # ⏳ pendente
├── carteira-service/        # ⏳ pendente
└── pom.xml                  # parent, multi-módulo, profiles A/B/C
```
