# Mapa de contextos

| Contexto | Responsabilidade | Dados próprios | Relações |
|---|---|---|---|
| Usuários | Identidade, credenciais e roles | usuários e hash BCrypt | emite JWT RS256 e fornece busca administrativa sem dados sensíveis |
| Partidas | Times e ciclo de vida da partida | times, partidas e eventos | publica `MATCH_CANCELED` pelo outbox |
| Apostas | Palpites e coordenação do estorno | palpites, cancelamentos, deduplicação e outbox | consome Partidas/Carteira e publica pedidos de pagamento/estorno |
| Carteira | Ledger imutável e saldo | carteiras, lançamentos e outbox | consome pedidos de Apostas e publica resultados de pagamento |
| Gateway | Borda HTTP, autenticação, autorização e composição | nenhum dado de domínio | valida JWT, encaminha identidade confiável e compõe atividade |

```text
Administrador -> Gateway -> Usuários / Partidas / Carteira
                            Partidas --MATCH_CANCELED--> Apostas
                            Apostas --BET_REFUND_REQUESTED--> Carteira
                            Carteira --PAYMENT_REFUNDED--> Apostas
```

Cada contexto protege novamente suas rotas administrativas. O gateway não acessa bancos e não vira fonte de verdade ao compor a linha do tempo.
