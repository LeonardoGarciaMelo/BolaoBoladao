import { expect, test } from "@playwright/test";

import {
  apiErrorMessage,
  activityEventLabel,
  betStatusLabel,
  matchStatusLabel,
  refundStatusLabel,
  walletOperationLabel,
  walletReasonLabel,
} from "../../src/scripts/presentation";

test("traduz códigos de domínio sem alterar os valores internos", () => {
  expect(matchStatusLabel("SCHEDULED")).toBe("AGENDADA");
  expect(matchStatusLabel("IN_PROGRESS")).toBe("AO VIVO");
  expect(betStatusLabel("PAYMENT_REFUSED")).toBe("PAGAMENTO RECUSADO");
  expect(betStatusLabel("AWAITING_SETTLEMENT")).toBe("AGUARDANDO APURAÇÃO");
  expect(refundStatusLabel("COMPLETED")).toBe("CONCLUÍDO");
  expect(walletReasonLabel("ADMIN_CREDIT")).toBe("Crédito administrativo");
  expect(activityEventLabel("MATCH_CANCELED")).toBe("Partida cancelada");
  expect(activityEventLabel("UNRECOGNIZED_EVENT")).toBe("Evento administrativo");
});

test("possui rótulos para todos os códigos apresentados", () => {
  for (const [code, label] of Object.entries({
    SCHEDULED: "AGENDADA", LIVE: "AO VIVO", IN_PROGRESS: "AO VIVO", FINISHED: "ENCERRADA", CANCELED: "CANCELADA",
  })) expect(matchStatusLabel(code)).toBe(label);
  for (const [code, label] of Object.entries({
    PROCESSING: "PROCESSANDO", CONFIRMED: "CONFIRMADO", AWAITING_SETTLEMENT: "AGUARDANDO APURAÇÃO",
    PAYMENT_REFUSED: "PAGAMENTO RECUSADO", CANCELING: "CANCELANDO", REFUNDING: "ESTORNANDO",
    CANCELED: "CANCELADO", REFUND_FAILED: "FALHA NO ESTORNO",
  })) expect(betStatusLabel(code)).toBe(label);
  for (const [code, label] of Object.entries({ PENDING: "PENDENTE", PROCESSING: "PROCESSANDO", COMPLETED: "CONCLUÍDO", FAILED: "FALHA" }))
    expect(refundStatusLabel(code)).toBe(label);
  for (const [code, label] of Object.entries({
    WIN: "Prêmio", DEPOSIT: "Depósito", BET: "Palpite", WITHDRAW: "Saque",
    ADMIN_CREDIT: "Crédito administrativo", BET_REFUND: "Estorno de palpite",
  })) expect(walletReasonLabel(code)).toBe(label);
  expect(walletOperationLabel("CREDIT")).toBe("Crédito");
  expect(walletOperationLabel("DEBIT")).toBe("Débito");
});

test("traduz erros conhecidos e protege a interface de mensagens desconhecidas", () => {
  expect(apiErrorMessage({ detail: "Match is not available for bets" }, "Falha ao enviar."))
    .toBe("Esta partida ainda não está disponível para palpites. Atualize e tente novamente.");
  expect(apiErrorMessage({ message: "Unexpected internal English error" }, "Falha ao enviar."))
    .toBe("Falha ao enviar.");
});
