import { expect, test } from "@playwright/test";
import { dialogBySelector, expectCenteredDialog } from "./helpers/dialog";

const match = {
  id: "11111111-1111-1111-1111-111111111111",
  teamHome: "Aurora",
  teamAway: "Estrela",
  teamHomeScore: 0,
  teamAwayScore: 0,
  start: new Date(Date.now() + 3_600_000).toISOString(),
  durationMinutes: 105,
  expectedEnd: new Date(Date.now() + (3_600_000 + 105 * 60_000)).toISOString(),
  end: null,
  status: "SCHEDULED",
  bettingOpen: true,
};

async function mockUserApis(page: import("@playwright/test").Page) {
  await page.addInitScript(() => sessionStorage.setItem("bolao.access-token", "user-token"));
  let balanceCents = 5000;
  let submission = 0;
  const forwardedKeys: string[] = [];
  await page.route("**/api/**", async (route) => {
    const url = new URL(route.request().url());
    if (url.pathname === "/api/auth/me") return route.fulfill({ json: { id: "user-1", name: "Ana Silva", username: "ana", roles: ["USER"] } });
    if (url.pathname === "/api/wallet/me") return route.fulfill({ json: { userId: "user-1", walletId: "wallet-1", balanceCents } });
    if (url.pathname === "/api/wallet/me/statement") return route.fulfill({ json: { items: [{ id: "entry-1", reason: "ADMIN_CREDIT", operation: "CREDIT", amountCents: 5000, occurredAt: new Date().toISOString(), note: "Boas-vindas" }, { id: "entry-2", reason: "BET", operation: "DEBIT", amountCents: 1000, occurredAt: new Date().toISOString() }], page: 0, size: 10, total: 2 } });
    if (url.pathname === "/api/wallet/me/deposits") return route.fulfill({ json: { items: [], page: 0, size: 10, total: 0 } });
    if (url.pathname === "/api/partidas/catalog") return route.fulfill({ json: { items: [match], page: 0, size: 12, total: 1 } });
    if (url.pathname === "/api/bets" && route.request().method() === "POST") {
      submission += 1;
      forwardedKeys.push(route.request().headers()["idempotency-key"]);
      return route.fulfill({ status: 201, json: bet("PROCESSING", submission === 1 ? "22222222-2222-2222-2222-222222222222" : "33333333-3333-3333-3333-333333333333") });
    }
    if (url.pathname.endsWith("/api/bets/22222222-2222-2222-2222-222222222222")) {
      balanceCents = 4000;
      return route.fulfill({ json: bet("AWAITING_SETTLEMENT", "22222222-2222-2222-2222-222222222222") });
    }
    if (url.pathname.endsWith("/api/bets/33333333-3333-3333-3333-333333333333")) return route.fulfill({ json: bet("PAYMENT_REFUSED", "33333333-3333-3333-3333-333333333333") });
    if (url.pathname === "/api/bets") return route.fulfill({ json: { items: [], page: 0, size: 10, total: 0 } });
    return route.fulfill({ status: 404, json: { message: "mock ausente" } });
  });
  return () => forwardedKeys;
}

function bet(status: string, id: string) {
  return {
    bet_id: id,
    user_id: "user-1",
    match_id: match.id,
    home_team_goals: 2,
    away_team_goals: 1,
    stake_amount: "10.00",
    status,
    created_at: new Date().toISOString(),
    updated_at: new Date().toISOString(),
    match: {
      match_id: match.id,
      team_home: match.teamHome,
      team_away: match.teamAway,
      scheduled_start: match.start,
      status: match.status,
      home_team_goals: 0,
      away_team_goals: 0,
    },
  };
}

async function submitMinimumBet(page: import("@playwright/test").Page) {
  await page.getByRole("button", { name: "FAZER PALPITE" }).click();
  await page.getByRole("button", { name: "Aumentar gols do mandante" }).click();
  await page.locator('[name="stake"]').fill("1,00");
  await page.getByRole("button", { name: "REVISAR PALPITE" }).click();
  await page.getByRole("button", { name: "CONFIRMAR PALPITE" }).click();
}

test("usuário revisa e confirma um palpite idempotente", async ({ page }) => {
  const forwardedKeys = await mockUserApis(page);
  await page.goto("/partidas");

  await expect(page.locator("[data-user-shell]")).toBeVisible();
  await expect(page.locator("[data-user-balance]")).toHaveText("R$ 50,00");
  await expect(page.getByRole("button", { name: "ABERTAS" })).toHaveAttribute("aria-pressed", "true");
  await expect(page.locator("[data-match-list]")).toContainText("Aurora");

  await page.getByRole("button", { name: "FAZER PALPITE" }).click();
  await expectCenteredDialog(dialogBySelector(page));
  await expect(page.locator('[name="homeGoals"]')).toHaveValue("0");
  await expect(page.getByRole("button", { name: "Diminuir gols do mandante" })).toBeDisabled();
  await page.getByRole("button", { name: "Aumentar gols do mandante" }).click();
  await page.getByRole("button", { name: "Aumentar gols do mandante" }).click();
  await page.getByRole("button", { name: "Aumentar gols do visitante" }).click();
  await page.locator('[name="stake"]').fill("10,00");
  await page.getByRole("button", { name: "REVISAR PALPITE" }).click();
  await expect(page.locator("[data-bet-review]")).toContainText("R$ 10,00");
  await page.getByRole("button", { name: "CONFIRMAR PALPITE" }).click();

  await expect(page.locator("[data-bet-result]")).toContainText("Processando pagamento");
  await expect(page.locator("[data-bet-result]")).toContainText("Palpite confirmado");
  await expect(page.locator("[data-user-balance]")).toHaveText("R$ 40,00");

  await page.getByRole("button", { name: "FECHAR", exact: true }).click();
  await page.getByRole("button", { name: "FAZER PALPITE" }).click();
  await page.locator('[name="homeGoals"]').fill("2");
  await page.locator('[name="awayGoals"]').fill("1");
  await page.locator('[name="stake"]').fill("10,00");
  await page.getByRole("button", { name: "REVISAR PALPITE" }).click();
  await page.getByRole("button", { name: "CONFIRMAR PALPITE" }).click();
  await expect(page.locator("[data-bet-result]")).toContainText("Pagamento recusado");
  await expect(page.locator("[data-user-balance]")).toHaveText("R$ 40,00");
  expect(forwardedKeys()).toHaveLength(2);
  expect(forwardedKeys()[0]).toBeTruthy();
  expect(forwardedKeys()[1]).not.toBe(forwardedKeys()[0]);
});

test("header e menu do avatar permanecem utilizáveis no mobile", async ({ page }) => {
  await page.setViewportSize({ width: 320, height: 700 });
  await mockUserApis(page);
  await page.goto("/perfil");
  await page.locator("[data-avatar-trigger]").click();
  await expect(page.getByRole("menuitem", { name: "Carteira" })).toBeVisible();
  await expect(page.getByRole("menuitem", { name: "Perfil" })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.locator("[data-avatar-trigger]").click();
  await page.getByRole("button", { name: "ADICIONAR SALDO" }).click();
  await expectCenteredDialog(dialogBySelector(page));
});

test("stepper aceita teclado e digitação, respeita limites e reinicia o placar", async ({ page }) => {
  await mockUserApis(page);
  await page.goto("/partidas");
  await page.getByRole("button", { name: "FAZER PALPITE" }).click();

  const homeGoals = page.locator('[name="homeGoals"]');
  const increaseHome = page.getByRole("button", { name: "Aumentar gols do mandante" });
  await homeGoals.fill("30");
  await expect(increaseHome).toBeDisabled();
  await homeGoals.press("ArrowDown");
  await expect(homeGoals).toHaveValue("29");
  await expect(increaseHome).toBeEnabled();

  await page.getByRole("button", { name: "Fechar" }).click();
  await page.getByRole("button", { name: "FAZER PALPITE" }).click();
  await expect(homeGoals).toHaveValue("0");
  await expect(page.getByRole("button", { name: "Diminuir gols do mandante" })).toBeDisabled();
});

test("diálogo de palpite permanece centralizado e sem overflow em 320px", async ({ page }) => {
  await page.setViewportSize({ width: 320, height: 700 });
  await mockUserApis(page);
  await page.goto("/partidas");
  await page.getByRole("button", { name: "FAZER PALPITE" }).click();
  await expectCenteredDialog(dialogBySelector(page));
  const dialog = page.getByRole("dialog");
  expect(await dialog.evaluate((element) => element.scrollWidth <= element.clientWidth)).toBe(true);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
});

test("diálogo alto usa rolagem interna e fecha com Escape", async ({ page }) => {
  await page.setViewportSize({ width: 320, height: 360 });
  await mockUserApis(page);
  await page.goto("/partidas");
  await page.getByRole("button", { name: "FAZER PALPITE" }).click();
  const dialog = dialogBySelector(page);
  await expectCenteredDialog(dialog);
  expect(await dialog.evaluate((element) => element.scrollHeight > element.clientHeight)).toBe(true);
  await page.keyboard.press("Escape");
  await expect(dialog).not.toBeVisible();
});

test("fecha o palpite quando a janela encerra durante a atualização", async ({ page }) => {
  await mockUserApis(page);
  let bettingOpen = true;
  await page.route(`**/api/partidas/${match.id}`, (route) => route.fulfill({
    json: { ...match, status: bettingOpen ? "SCHEDULED" : "IN_PROGRESS", bettingOpen },
  }));
  await page.goto("/partidas");
  await page.getByRole("button", { name: "FAZER PALPITE" }).click();
  bettingOpen = false;
  await expect(page.getByRole("dialog")).not.toBeVisible({ timeout: 7_000 });
  await expect(page.locator("[data-match-notice]")).toHaveText("Palpites encerrados para esta partida");
});

test("erro inglês da API de palpites é apresentado em português", async ({ page }) => {
  await mockUserApis(page);
  await page.route("**/api/bets", async (route) => {
    if (route.request().method() === "POST") {
      return route.fulfill({ status: 409, json: { detail: "Match is not available for bets" } });
    }
    return route.fallback();
  });
  await page.goto("/partidas");
  await submitMinimumBet(page);
  await expect(page.locator("[data-review-message]"))
    .toHaveText("Esta partida ainda não está disponível para palpites. Atualize e tente novamente.");
  await expect(page.locator("[data-review-message]")).not.toContainText("Match is not available");
});

test("erro inglês desconhecido usa fallback contextual em português", async ({ page }) => {
  await mockUserApis(page);
  await page.route("**/api/bets", async (route) => {
    if (route.request().method() === "POST") {
      return route.fulfill({ status: 500, json: { detail: "Unexpected internal English error" } });
    }
    return route.fallback();
  });
  await page.goto("/partidas");
  await submitMinimumBet(page);
  await expect(page.locator("[data-review-message]")).toHaveText("Não foi possível enviar o palpite.");
  await expect(page.locator("[data-review-message]")).not.toContainText("Unexpected internal English error");
});

test("navega pelas quatro páginas, abre saldo e apresenta o extrato", async ({ page }) => {
  await mockUserApis(page);
  await page.goto("/partidas");
  await page.getByRole("link", { name: "Palpites" }).click();
  await expect(page.getByRole("heading", { name: "Meus palpites" })).toBeVisible();

  await page.locator("[data-avatar-trigger]").click();
  await page.getByRole("menuitem", { name: "Carteira" }).click();
  await expect(page.getByRole("heading", { name: "Histórico de transações" })).toBeVisible();
  await expect(page.locator("[data-wallet-history]")).toContainText("Crédito administrativo");
  await expect(page.locator("[data-wallet-history]")).toContainText("− R$ 10,00");
  await page.getByRole("button", { name: "ADICIONAR SALDO" }).first().click();
  await expect(page.getByRole("dialog")).toContainText("Escolha quanto deseja adicionar");
  await page.getByRole("button", { name: "Fechar" }).click();

  await page.locator("[data-avatar-trigger]").click();
  await page.getByRole("menuitem", { name: "Perfil" }).click();
  await expect(page.getByRole("heading", { name: "Ana Silva" })).toBeVisible();
});

test("modal de depósito revisa valor e encaminha chave idempotente", async ({ page }) => {
  await mockUserApis(page);
  let forwardedKey = "";
  await page.route("**/api/wallet/me/deposits", async (route) => {
    if (route.request().method() === "POST") {
      forwardedKey = route.request().headers()["idempotency-key"];
      expect(route.request().postDataJSON()).toEqual({ amountCents: 5000 });
      return route.fulfill({ status: 201, json: { depositId: "deposit-1", amountCents: 5000, status: "PENDING", checkoutUrl: "/pagamento#checkout-token" } });
    }
    return route.fulfill({ json: { items: [], page: 0, size: 10, total: 0 } });
  });
  await page.route("**/api/v1/checkout", (route) => route.fulfill({ json: { chargeId: "charge-1", amountCents: 5000, status: "PENDING", returnUrl: "/carteira?depositId=deposit-1", expiresAt: new Date(Date.now() + 900_000).toISOString() } }));
  await page.goto("/carteira");
  await page.getByRole("button", { name: "ADICIONAR SALDO" }).first().click();
  await page.getByRole("button", { name: "R$ 50,00" }).click();
  await page.getByRole("button", { name: "REVISAR DEPÓSITO" }).click();
  await expect(page.getByRole("dialog")).toContainText("PIX fictício");
  await page.getByRole("button", { name: "IR PARA PAGAMENTO" }).click();
  await expect(page).toHaveURL(/\/pagamento$/);
  expect(forwardedKey).toBeTruthy();
});

test("checkout sandbox exibe QR fictício e simula pagamento", async ({ page }) => {
  const charge = { chargeId: "charge-1", amountCents: 5000, status: "PENDING", returnUrl: "/carteira?depositId=deposit-1", expiresAt: new Date(Date.now() + 900_000).toISOString() };
  await page.route("**/api/v1/checkout", async (route) => {
    expect(route.request().headers().authorization).toBe("Checkout checkout-token");
    return route.fulfill({ json: charge });
  });
  await page.route("**/api/v1/checkout/simulate", async (route) => {
    expect(route.request().postDataJSON()).toEqual({ outcome: "PAID" });
    return route.fulfill({ json: { ...charge, status: "PAID" } });
  });
  await page.goto("/pagamento#checkout-token");
  await expect(page.getByRole("heading", { name: "Finalize seu depósito" })).toBeVisible();
  await expect(page.locator("[data-payment-code]")).toHaveText("https://youtu.be/dQw4w9WgXcQ");
  await expect(page.locator(".payment-qr svg")).toBeVisible();
  await page.getByRole("button", { name: "SIMULAR PAGAMENTO" }).click();
  await expect(page.locator("[data-payment-result]")).toContainText("Pagamento confirmado");
  await expect(page.getByRole("link", { name: "VOLTAR À CARTEIRA" })).toHaveAttribute("href", /\/carteira\?depositId=deposit-1$/);
});

for (const scenario of [
  { outcome: "REFUSED", button: "SIMULAR RECUSA", expected: "Pagamento recusado" },
  { outcome: "EXPIRED", button: "SIMULAR EXPIRAÇÃO", expected: "Cobrança expirada" },
]) {
  test(`checkout apresenta ${scenario.outcome.toLowerCase()} em português`, async ({ page }) => {
    const charge = { chargeId: "charge-terminal", amountCents: 2000, status: "PENDING", returnUrl: "/carteira?depositId=deposit-terminal", expiresAt: new Date(Date.now() + 900_000).toISOString() };
    await page.route("**/api/v1/checkout", (route) => route.fulfill({ json: charge }));
    await page.route("**/api/v1/checkout/simulate", async (route) => {
      expect(route.request().postDataJSON()).toEqual({ outcome: scenario.outcome });
      return route.fulfill({ json: { ...charge, status: scenario.outcome } });
    });
    await page.goto("/pagamento#checkout-token");
    await page.getByRole("button", { name: scenario.button }).click();
    await expect(page.locator("[data-payment-result]")).toContainText(scenario.expected);
  });
}

test("checkout mostra falha de simulação e não possui overflow em 320px", async ({ page }) => {
  await page.setViewportSize({ width: 320, height: 700 });
  const charge = { chargeId: "charge-error", amountCents: 2000, status: "PENDING", returnUrl: "/carteira?depositId=deposit-error", expiresAt: new Date(Date.now() + 900_000).toISOString() };
  await page.route("**/api/v1/checkout", (route) => route.fulfill({ json: charge }));
  await page.route("**/api/v1/checkout/simulate", (route) => route.fulfill({ status: 503 }));
  await page.goto("/pagamento#checkout-token");
  await page.getByRole("button", { name: "SIMULAR PAGAMENTO" }).click();
  await expect(page.locator("[data-payment-message]")).toHaveText("Não foi possível registrar a simulação. Tente novamente.");
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
});

test("carteira apresenta depósitos recentes e permite retomar preparação", async ({ page }) => {
  await mockUserApis(page);
  let reconciled = "";
  const now = new Date().toISOString();
  await page.route(/\/api\/wallet\/me\/deposits(?:\?.*)?$/, (route) => route.fulfill({ json: { items: [
    { depositId: "deposit-creating", amountCents: 2000, status: "CREATING", createdAt: now, updatedAt: now },
    { depositId: "deposit-pending", amountCents: 5000, status: "PENDING", checkoutUrl: "/pagamento#pending", createdAt: now, updatedAt: now },
    { depositId: "deposit-confirmed", amountCents: 10000, status: "CONFIRMED", createdAt: now, updatedAt: now, confirmedAt: now },
  ], page: 0, size: 10, total: 3 } }));
  await page.route("**/api/wallet/me/deposits/*/reconcile", async (route) => {
    reconciled = new URL(route.request().url()).pathname;
    return route.fulfill({ json: { depositId: "deposit-creating", amountCents: 2000, status: "PENDING", checkoutUrl: "/pagamento#prepared", createdAt: now, updatedAt: now } });
  });

  await page.goto("/carteira");
  await expect(page.locator("[data-deposit-list]")).toContainText("PREPARANDO");
  await expect(page.locator("[data-deposit-list]")).toContainText("AGUARDANDO PAGAMENTO");
  await expect(page.locator("[data-deposit-list]")).toContainText("CONFIRMADO");
  await page.getByRole("button", { name: "TENTAR PREPARAR" }).click();
  expect(reconciled).toContain("/deposit-creating/reconcile");
});
