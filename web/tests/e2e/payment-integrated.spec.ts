import { expect, test } from "@playwright/test";

async function registerAndLogin(page: import("@playwright/test").Page, prefix: string) {
  const username = `${prefix}${Date.now()}${Math.floor(Math.random() * 1000)}`;
  const password = "Boladao-Pay-2026!";
  await page.goto("/registro");
  await page.locator("#name").fill("Teste PIX");
  await page.locator("#username").fill(username);
  await page.locator("#password").fill(password);
  await page.getByRole("button", { name: "CRIAR CONTA" }).click();
  await expect(page).toHaveURL(/\/login\?registered=1$/);
  await page.locator("#username").fill(username);
  await page.locator("#password").fill(password);
  await page.getByRole("button", { name: "ENTRAR" }).click();
  await expect(page).toHaveURL(/\/partidas$/);
}

async function walletApi(page: import("@playwright/test").Page, path: string, init: { method?: string; headers?: Record<string, string>; body?: string } = {}) {
  return page.evaluate(async ({ path, init }) => {
    const token = sessionStorage.getItem("bolao.access-token");
    const response = await fetch(`/api${path}`, { ...init, headers: { ...init.headers, Authorization: `Bearer ${token}` } });
    return { status: response.status, body: await response.json() };
  }, { path, init });
}

async function createDeposit(page: import("@playwright/test").Page, amountCents: number) {
  const response = await walletApi(page, "/wallet/me/deposits", {
    method: "POST",
    headers: { "Content-Type": "application/json", "Idempotency-Key": crypto.randomUUID() },
    body: JSON.stringify({ amountCents }),
  });
  expect(response.status).toBe(201);
  return response.body as { depositId: string; checkoutUrl: string; status: string };
}

test("fluxo integrado cria, paga e credita um depósito PIX fictício", async ({ page }) => {
  test.setTimeout(60_000);
  await registerAndLogin(page, "pix");

  await page.getByRole("button", { name: "ADICIONAR SALDO" }).click();
  await page.getByRole("button", { name: "R$ 20,00" }).click();
  await page.getByRole("button", { name: "REVISAR DEPÓSITO" }).click();
  await page.getByRole("button", { name: "IR PARA PAGAMENTO" }).click();
  await expect(page).toHaveURL(/\/pagamento$/);
  await expect(page.locator("[data-payment-amount]")).toHaveText("R$ 20,00");
  await expect(page.locator("[data-payment-code]")).toHaveText("https://youtu.be/dQw4w9WgXcQ");

  await page.getByRole("button", { name: "SIMULAR PAGAMENTO" }).click();
  await expect(page.locator("[data-payment-result]")).toContainText("Pagamento confirmado");
  await page.getByRole("link", { name: "VOLTAR À CARTEIRA" }).click();

  await expect(page).toHaveURL(/\/carteira(?:\?depositId=.*)?$/, { timeout: 10_000 });
  await expect(page.locator("[data-wallet-page-balance]")).toHaveText("R$ 20,00", { timeout: 20_000 });
  await expect(page.locator("[data-deposit-list]")).toContainText("CONFIRMADO");
  await expect(page.locator("[data-wallet-history]")).toContainText("Depósito");
  await expect(page.locator("[data-wallet-history]")).toContainText("+ R$ 20,00");
});

test("resultados terminais concorrentes possuem um único vencedor no Postgres", async ({ page }) => {
  test.setTimeout(60_000);
  await registerAndLogin(page, "race");
  const deposit = await createDeposit(page, 3000);
  const checkoutToken = new URL(deposit.checkoutUrl).hash.slice(1);

  const results = await page.evaluate(async (token) => {
    const base = location.hostname === "api-gateway" ? "http://payment-simulator:4000/api/v1" : "http://localhost:4000/api/v1";
    const simulate = (outcome: string) => fetch(`${base}/checkout/simulate`, {
      method: "POST",
      headers: { Authorization: `Checkout ${token}`, "Content-Type": "application/json" },
      body: JSON.stringify({ outcome }),
    }).then(async (response) => ({ status: response.status, body: await response.json() }));
    return Promise.all([simulate("PAID"), simulate("REFUSED")]);
  }, checkoutToken);

  expect(results.map((result) => result.status).sort()).toEqual([200, 409]);
  const winner = results.find((result) => result.status === 200)?.body.status;
  expect(["PAID", "REFUSED"]).toContain(winner);
  expect(results.find((result) => result.status === 409)?.body.status).toBe(winner);
});

test("webhook e reconciliação concorrentes criam um único crédito real", async ({ page }) => {
  test.setTimeout(60_000);
  await registerAndLogin(page, "ledger");
  const deposit = await createDeposit(page, 2000);
  const checkoutToken = new URL(deposit.checkoutUrl).hash.slice(1);

  await page.evaluate(async ({ token, depositId }) => {
    const accessToken = sessionStorage.getItem("bolao.access-token");
    const provider = location.hostname === "api-gateway" ? "http://payment-simulator:4000/api/v1" : "http://localhost:4000/api/v1";
    await Promise.all([
      fetch(`${provider}/checkout/simulate`, {
        method: "POST",
        headers: { Authorization: `Checkout ${token}`, "Content-Type": "application/json" },
        body: JSON.stringify({ outcome: "PAID" }),
      }),
      fetch(`/api/wallet/me/deposits/${depositId}/reconcile`, {
        method: "POST",
        headers: { Authorization: `Bearer ${accessToken}` },
      }),
    ]);
  }, { token: checkoutToken, depositId: deposit.depositId });

  await expect.poll(async () => (await walletApi(page, `/wallet/me/deposits/${deposit.depositId}`)).body.status,
    { timeout: 20_000 }).toBe("CONFIRMED");
  const statement = await walletApi(page, "/wallet/me/statement?page=0&size=50");
  const credits = statement.body.items.filter((entry: { reason: string; referenceId?: string }) =>
    entry.reason === "DEPOSIT" && entry.referenceId === deposit.depositId);
  expect(credits).toHaveLength(1);
  expect(credits[0].amountCents).toBe(2000);
  expect((await walletApi(page, "/wallet/me")).body.balanceCents).toBe(2000);
});
