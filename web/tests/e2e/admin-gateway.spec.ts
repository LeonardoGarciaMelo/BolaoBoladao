import { expect, test } from "@playwright/test";

const admin = { username: "admin", password: "admin-seguro-123" };

async function login(page: import("@playwright/test").Page, credentials = admin) {
  await page.goto("/login");
  await page.locator("#username").fill(credentials.username);
  await page.locator("#password").fill(credentials.password);
  await page.locator("[data-auth-form] button[type=submit]").click();
}

test("redireciona por role e preserva 403 como acesso negado", async ({ page, request }) => {
  const username = `user-${Date.now()}`;
  const password = "senha-segura-123";
  const register = await request.post("/api/auth/register", {
    data: { name: "Usuário Operacional", username, password },
  });
  expect(register.status()).toBe(201);

  await login(page, { username, password });
  await expect(page).toHaveURL(/\/partidas\/?$/);
  await page.goto("/admin");
  await expect(page.locator("[data-admin-denied]")).toBeVisible();
  await expect(page.locator("[data-admin-denied]")).toContainText("ACESSO RESTRITO");

  await page.evaluate(() => sessionStorage.clear());
  await login(page);
  await expect(page).toHaveURL(/\/admin\/?$/);
  await expect(page.locator("[data-admin-shell]")).toBeVisible();
});

test("admin cria e cancela partida, concede crédito e consulta auditoria", async ({ page, request }) => {
  const suffix = Date.now().toString().slice(-7);
  const username = `credito-${suffix}`;
  const register = await request.post("/api/auth/register", {
    data: { name: "Torcedor do Crédito", username, password: "senha-segura-123" },
  });
  expect(register.status()).toBe(201);

  await login(page);
  await expect(page).toHaveURL(/\/admin\/?$/);

  await page.goto("/admin/partidas");
  await expect(page.locator("[data-admin-shell]")).toBeVisible();
  const home = `Aurora ${suffix}`;
  const away = `Estrela ${suffix}`;
  await page.locator('[name="teamHomeName"]').fill(home);
  await page.locator('[name="teamAwayName"]').fill(away);
  const future = new Date(Date.now() + 86_400_000);
  await page.locator('[name="start"]').fill(future.toISOString().slice(0, 16));
  await page.getByRole("button", { name: "CRIAR PARTIDA" }).click();
  await expect(page.locator("[data-match-message]")).toContainText("Partida criada");

  const row = page.locator(".admin-match-row", { hasText: `${home} × ${away}` });
  await expect(row).toBeVisible();
  await row.getByRole("button", { name: "CANCELAR" }).click();
  await page.locator("[data-cancel-dialog] textarea").fill("Cancelamento administrativo pelo teste E2E");
  await page.locator('[data-cancel-dialog] button[value="confirm"]').click();
  await expect(page.locator(".admin-match-row", { hasText: `${home} × ${away}` })).toContainText("CANCELED");
  await expect(page.locator(".admin-match-row", { hasText: `${home} × ${away}` }).locator(".admin-match-refund span"))
    .toContainText(/COMPLETED|PROCESSANDO/, { timeout: 15_000 });

  await page.goto("/admin/carteiras");
  await page.locator('[data-user-search] input[name="q"]').fill(username);
  await page.locator("[data-user-search] button").click();
  await page.locator("[data-user-results] button", { hasText: `@${username}` }).click();
  await expect(page.locator("[data-selected-user]")).toContainText("Saldo");
  await page.locator('[data-credit-form] input[name="amount"]').fill("12,34");
  await page.locator('[data-credit-form] textarea[name="reason"]').fill("Crédito administrativo pelo teste E2E");
  await page.getByRole("button", { name: "REVISAR CRÉDITO" }).click();
  await expect(page.locator("[data-credit-dialog]")).toBeVisible();
  await expect(page.locator("[data-review-after]")).toContainText("12,34");
  await page.getByRole("button", { name: "CONFIRMAR CRÉDITO" }).click();
  await expect(page.locator("[data-receipt-dialog]")).toBeVisible();
  await expect(page.locator("[data-receipt-copy]")).toContainText(`@${username}`);
  await page.locator("[data-receipt-close]").click();

  await page.goto("/admin");
  await expect(page.locator("[data-activity]")).toContainText("Crédito administrativo", { timeout: 10_000 });
  await expect(page.locator("[data-metric-refunds]")).not.toHaveText("—");
});

test("atividade administrativa pagina sem perder eventos", async ({ page, request }) => {
  await login(page);
  await expect(page).toHaveURL(/\/admin\/?$/);
  const token = await page.evaluate(() => sessionStorage.getItem("bolao.access-token"));
  expect(token).toBeTruthy();
  const headers = { Authorization: `Bearer ${token}` };
  const suffix = Date.now().toString().slice(-7);

  for (let index = 0; index < 21; index += 1) {
    const response = await request.post("/api/admin/partidas", {
      headers,
      data: {
        teamHomeName: `Paginação A ${suffix}`,
        teamAwayName: `Paginação B ${suffix}`,
        start: new Date(Date.now() + 86_400_000 + index * 60_000).toISOString(),
      },
    });
    expect(response.status()).toBe(201);
  }

  const firstResponse = await request.get("/api/admin/activity?size=5", { headers });
  expect(firstResponse.ok()).toBe(true);
  const first = await firstResponse.json();
  expect(first.items).toHaveLength(5);
  expect(first.nextCursor).toBeTruthy();
  const insertedAfterSnapshot = await request.post("/api/admin/partidas", {
    headers,
    data: {
      teamHomeName: `Snapshot A ${suffix}`,
      teamAwayName: `Snapshot B ${suffix}`,
      start: new Date(Date.now() + 172_800_000).toISOString(),
    },
  });
  expect(insertedAfterSnapshot.status()).toBe(201);
  const secondResponse = await request.get(
    `/api/admin/activity?size=5&cursor=${encodeURIComponent(first.nextCursor)}`,
    { headers },
  );
  expect(secondResponse.ok()).toBe(true);
  const second = await secondResponse.json();
  expect(second.total).toBe(first.total);
  const firstIds = new Set(first.items.map((item: { type: string; resourceId: string }) => `${item.type}:${item.resourceId}`));
  expect(second.items.some((item: { type: string; resourceId: string }) => firstIds.has(`${item.type}:${item.resourceId}`))).toBe(false);

  await page.goto("/admin");
  await expect(page.locator("[data-load-more]")).toBeVisible();
  const initialRows = await page.locator("[data-activity] article").count();
  await page.locator("[data-load-more]").click();
  await expect.poll(() => page.locator("[data-activity] article").count()).toBeGreaterThan(initialRows);
});

for (const viewport of [{ width: 390, height: 844 }, { width: 768, height: 1024 }]) {
  test(`admin não possui overflow em ${viewport.width}px`, async ({ page }) => {
    await page.setViewportSize(viewport);
    await login(page);
    await expect(page).toHaveURL(/\/admin\/?$/);
    for (const route of ["/admin", "/admin/partidas", "/admin/carteiras"]) {
      await page.goto(route);
      await expect(page.locator("[data-admin-shell]")).toBeVisible();
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    }
  });
}
