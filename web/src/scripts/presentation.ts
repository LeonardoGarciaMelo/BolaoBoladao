const MATCH_STATUS: Record<string, string> = {
  SCHEDULED: "AGENDADA",
  LIVE: "AO VIVO",
  IN_PROGRESS: "AO VIVO",
  FINISHED: "ENCERRADA",
  CANCELED: "CANCELADA",
};

const BET_STATUS: Record<string, string> = {
  PROCESSING: "PROCESSANDO",
  CONFIRMED: "CONFIRMADO",
  AWAITING_SETTLEMENT: "AGUARDANDO APURAÇÃO",
  WON: "GANHOU",
  LOST: "PERDEU",
  PAYMENT_REFUSED: "PAGAMENTO RECUSADO",
  CANCELING: "CANCELANDO",
  REFUNDING: "ESTORNANDO",
  CANCELED: "CANCELADO",
  REFUND_FAILED: "FALHA NO ESTORNO",
};

const REFUND_STATUS: Record<string, string> = {
  PENDING: "PENDENTE",
  PROCESSING: "PROCESSANDO",
  COMPLETED: "CONCLUÍDO",
  FAILED: "FALHA",
};

const WALLET_REASON: Record<string, string> = {
  WIN: "Prêmio",
  DEPOSIT: "Depósito",
  BET: "Palpite",
  WITHDRAW: "Saque",
  ADMIN_CREDIT: "Crédito administrativo",
  BET_REFUND: "Estorno de palpite",
};

const WALLET_OPERATION: Record<string, string> = {
  CREDIT: "Crédito",
  DEBIT: "Débito",
};

const DEPOSIT_STATUS: Record<string, string> = {
  CREATING: "PREPARANDO",
  PENDING: "AGUARDANDO PAGAMENTO",
  CONFIRMED: "CONFIRMADO",
  REFUSED: "RECUSADO",
  EXPIRED: "EXPIRADO",
};

const ACTIVITY_EVENT: Record<string, string> = {
  MATCH_CREATED: "Partida criada",
  MATCH_STARTED: "Partida iniciada",
  TEAM_HOME_SCORED: "Gol do mandante",
  TEAM_AWAY_SCORED: "Gol do visitante",
  TEAM_HOME_GOAL_ANNULLED: "Gol do mandante anulado",
  TEAM_AWAY_GOAL_ANNULLED: "Gol do visitante anulado",
  MATCH_ENDED: "Partida encerrada",
  MATCH_CANCELED: "Partida cancelada",
  ADMIN_CREDIT: "Crédito administrativo",
};

const API_ERRORS: Record<string, string> = {
  "Missing authenticated user": "Sessão do usuário não encontrada.",
  "Invalid authenticated user": "Não foi possível validar a identidade do usuário.",
  "Missing bearer token": "Sessão não encontrada. Entre novamente.",
  "Invalid bearer token": "Sua sessão é inválida ou expirou. Entre novamente.",
  "Admin role required": "Acesso restrito a administradores.",
  "Idempotency-Key is required": "Não foi possível identificar esta tentativa. Tente novamente.",
  "Idempotency-Key already used with another payload": "Este envio já foi usado com dados diferentes. Revise e tente novamente.",
  "Match is not available for bets": "Esta partida ainda não está disponível para palpites. Atualize e tente novamente.",
  "Match projection is unavailable": "Os dados desta partida ainda estão sendo sincronizados. Atualize e tente novamente.",
  "Betting window is closed": "A janela de palpites desta partida está encerrada.",
  "Bet not found": "Palpite não encontrado.",
  "Cancellation not found": "Cancelamento não encontrado.",
};

const normalizeCode = (value: unknown) => String(value ?? "").trim().toUpperCase();

export const matchStatusLabel = (status: unknown) => MATCH_STATUS[normalizeCode(status)] ?? "STATUS DESCONHECIDO";
export const betStatusLabel = (status: unknown) => BET_STATUS[normalizeCode(status)] ?? "STATUS DESCONHECIDO";
export const refundStatusLabel = (status: unknown) => REFUND_STATUS[normalizeCode(status)] ?? "STATUS DESCONHECIDO";
export const walletReasonLabel = (reason: unknown) => WALLET_REASON[normalizeCode(reason)] ?? "Movimentação";
export const walletOperationLabel = (operation: unknown) => WALLET_OPERATION[normalizeCode(operation)] ?? "Operação";
export const depositStatusLabel = (status: unknown) => DEPOSIT_STATUS[normalizeCode(status)] ?? "STATUS DESCONHECIDO";
export const activityEventLabel = (type: unknown) => ACTIVITY_EVENT[normalizeCode(type)] ?? "Evento administrativo";

const rawApiMessage = (payload: unknown): string => {
  if (typeof payload === "string") return payload.trim();
  if (!payload || typeof payload !== "object") return "";
  const candidate = payload as { detail?: unknown; message?: unknown; error?: unknown };
  for (const value of [candidate.detail, candidate.message, candidate.error]) {
    if (typeof value === "string" && value.trim()) return value.trim();
    if (value && typeof value === "object" && "message" in value) {
      const message = (value as { message?: unknown }).message;
      if (typeof message === "string" && message.trim()) return message.trim();
    }
  }
  return "";
};

export const apiErrorMessage = (payload: unknown, fallback: string) => API_ERRORS[rawApiMessage(payload)] ?? fallback;

export const readApiError = async (response: Response, fallback: string) => {
  const payload = await response.clone().json().catch(() => undefined);
  return apiErrorMessage(payload, fallback);
};
