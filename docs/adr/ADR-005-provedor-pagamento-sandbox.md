# ADR-005 — Provedor de pagamento sandbox

## Status

Aceito e implementado.

## Contexto

Adicionar saldo precisa exercitar falhas, idempotência, notificações duplicadas e consistência eventual sem conectar o projeto a um meio de pagamento real nem permitir que outro serviço escreva no ledger da Carteira.

## Decisão

O `payment-simulator` representa um sistema externo simulado, implementado como API Phoenix com Postgres e Oban próprios. Ele cria cobranças PIX fictícias por HTTP, oferece um checkout público autenticado por token opaco e entrega resultados terminais à Carteira por webhook HMAC-SHA256. Não usa Kafka e nunca acessa o banco da Carteira.

A Carteira permanece dona do saldo. Ela persiste uma solicitação em `CREATING` antes da chamada externa e reutiliza `deposit:<depositId>` no provedor. Webhook e reconciliação convergem no mesmo caso de uso transacional; somente `PAID` cria `DEPOSIT/CREDIT`, protegido por `deposit-credit:<depositId>`. `REFUSED` e `EXPIRED` não alteram o ledger.

As assinaturas cobrem `<timestamp>.<corpo bruto>`, têm tolerância de cinco minutos e comparação constante. O provedor repete webhooks com backoff pelo Oban mantendo UUID, instante terminal e payload estáveis; após dez falhas registra a entrega como `FAILED`. A Carteira deduplica o UUID do evento e valida cobrança, referência e valor antes de qualquer transição.

## Consequências

O fluxo reproduz características de uma integração externa sem pagamento real e sem ampliar os quatro bounded contexts centrais. Há consistência eventual entre o resultado da cobrança e o saldo, por isso a interface expõe reconciliação e estados intermediários. O simulador é apenas infraestrutura de desenvolvimento e deve permanecer restrito a origens e portas locais.
