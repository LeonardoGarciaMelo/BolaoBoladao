# Boladão Pay Sandbox

Provedor externo simulado para cobranças PIX fictícias. Usa Elixir 1.20.1,
OTP 28, Phoenix 1.8.9, PostgreSQL e Oban. O serviço não acessa a Carteira e não
usa Kafka.

## APIs

- `POST /api/v1/merchant/charges` e `GET /api/v1/merchant/charges/:id` usam
  `Authorization: Bearer <merchant-key>`; a criação exige `Idempotency-Key`.
- `GET /api/v1/checkout` e `POST /api/v1/checkout/simulate` usam
  `Authorization: Checkout <token>`.
- cobranças aceitam 100 a 1.000.000 centavos, expiram em 15 minutos e terminam
  como `PAID`, `REFUSED` ou `EXPIRED`.

O webhook é assinado com HMAC-SHA256 sobre `<timestamp>.<corpo bruto>` e
reenviado pelo Oban até dez vezes. O UUID, o instante terminal e o payload do
evento permanecem estáveis entre as tentativas. Qualquer resposta `2xx`
confirma a entrega; depois da última falha, a cobrança expõe o webhook como
`FAILED` para diagnóstico.

## Execução e testes

O caminho recomendado é o Compose da raiz. O serviço publica somente
`127.0.0.1:4000`.

```bash
docker compose up -d --build payment-simulator
docker compose --profile test run --build --rm payment-simulator-tests
```

Para desenvolvimento isolado, configure Postgres e as variáveis descritas em
`config/runtime.exs`, rode `mix setup` e `mix phx.server`. Ao finalizar mudanças,
execute `mix precommit`.
