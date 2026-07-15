import { authenticatedFetch } from "./session";

export interface WalletSummary {
  userId: string;
  walletId: string;
  balanceCents: number;
}

export const currency = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" });
export const formatCents = (value: number) => currency.format(value / 100);

export const getWalletSummary = async () => {
  const response = await authenticatedFetch("/wallet/me");
  if (!response.ok) throw new Error("Não foi possível carregar sua carteira.");
  return response.json() as Promise<WalletSummary>;
};

export const announceWallet = (wallet: WalletSummary) => {
  window.dispatchEvent(new CustomEvent<WalletSummary>("wallet:updated", { detail: wallet }));
};

export const refreshWallet = async () => {
  const wallet = await getWalletSummary();
  announceWallet(wallet);
  return wallet;
};

export const errorMessage = (error: unknown, fallback: string) => error instanceof Error ? error.message : fallback;
