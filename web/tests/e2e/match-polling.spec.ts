import { expect, test, type Page, type Route } from "@playwright/test";

type Match = {
  id: string;
  teamHome: string;
  teamAway: string;
  teamHomeScore: number;
  teamAwayScore: number;
  start: string;
  durationMinutes: number;
  expectedEnd: string;
  startedAt: string;
  end: null;
  status: string;
  bettingOpen: boolean;
};

const makeMatch = (index: number, overrides: Partial<Match> = {}): Match => ({
  id: `00000000-0000-0000-0000-${String(index).padStart(12, "0")}`,
  teamHome: `Mandante ${index}`,
  teamAway: `Visitante ${index}`,
  teamHomeScore: 0,
  teamAwayScore: 0,
  start: new Date(Date.now() - 60_000).toISOString(),
  durationMinutes: 105,
  expectedEnd: new Date(Date.now() + 6_240_000).toISOString(),
  startedAt: new Date(Date.now() - 60_000).toISOString(),
  end: null,
  status: "IN_PROGRESS",
  bettingOpen: false,
  ...overrides,
});

async function installSession(page: Page) {
  await page.addInitScript(() => sessionStorage.setItem("bolao.access-token", "test-token"));
}

function fulfillSession(route: Route, admin = false) {
  return route.fulfill({ json: { id: "user-1", name: "Ana Silva", username: "ana", roles: admin ? ["ADMIN"] : ["USER"] } });
}

async function mockEmptyAdminMatches(page: Page) {
  await installSession(page);
  await page.route("**/api/**", (route) => {
    const url = new URL(route.request().url());
    if (url.pathname === "/api/auth/me") return fulfillSession(route, true);
    if (url.pathname === "/api/admin/partidas") return route.fulfill({ json: { items: [], page: 0, size: 50, total: 0 } });
    return route.fulfill({ status: 404, json: {} });
  });
}

test("formulário coloca data acima de horário e duração no desktop", async ({ page }) => {
  await mockEmptyAdminMatches(page);
  await page.goto("/admin/partidas");

  const boxes = await page.locator(".admin-datetime-fields label").evaluateAll((labels) => labels.map((label) => {
    const { x, y, width, height } = label.getBoundingClientRect();
    return { x, y, width, height };
  }));
  expect(boxes[0].y + boxes[0].height).toBeLessThanOrEqual(boxes[1].y);
  expect(Math.abs(boxes[1].y - boxes[2].y)).toBeLessThan(2);
  expect(boxes[1].x + boxes[1].width).toBeLessThanOrEqual(boxes[2].x);
});

for (const width of [320, 390, 640]) {
  test(`formulário empilha os campos sem overflow em ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 800 });
    await mockEmptyAdminMatches(page);
    await page.goto("/admin/partidas");

    const y = await page.locator(".admin-datetime-fields label").evaluateAll((labels) => labels.map((label) => label.getBoundingClientRect().y));
    expect(y[0]).toBeLessThan(y[1]);
    expect(y[1]).toBeLessThan(y[2]);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  });
}

test("admin atualiza o placar preservando card, foco e lista", async ({ page }) => {
  await installSession(page);
  let calls = 0;
  await page.route("**/api/**", async (route) => {
    const url = new URL(route.request().url());
    if (url.pathname === "/api/auth/me") return fulfillSession(route, true);
    if (url.pathname === "/api/admin/partidas") {
      calls += 1;
      const score = calls > 1 ? 1 : 0;
      const items = calls > 1 ? [makeMatch(2), makeMatch(1, { teamHomeScore: score })] : [makeMatch(1), makeMatch(2)];
      return route.fulfill({ json: { items, page: 0, size: 50, total: 2 } });
    }
    return route.fulfill({ status: 404, json: {} });
  });
  await page.goto("/admin/partidas");
  const card = page.locator('[data-match-id="00000000-0000-0000-0000-000000000001"]');
  await expect(card).toBeVisible();
  await card.evaluate((element) => {
    element.setAttribute("data-identity", "original");
    (window as typeof window & { matchListBecameEmpty?: boolean }).matchListBecameEmpty = false;
    const list = element.parentElement!;
    new MutationObserver(() => {
      if (list.childElementCount === 0) (window as typeof window & { matchListBecameEmpty?: boolean }).matchListBecameEmpty = true;
    }).observe(list, { childList: true });
  });
  const focused = card.getByRole("button", { name: /Adicionar gol de Mandante 1/i });
  await focused.focus();
  await page.evaluate(() => { document.body.style.minHeight = "2200px"; window.scrollTo(0, 300); });

  await expect(card.locator(".admin-live-score")).toHaveText("1 × 0", { timeout: 7_000 });
  await expect(card).toHaveAttribute("data-identity", "original");
  await expect(page.locator("[data-match-id]").nth(1)).toHaveAttribute("data-match-id", makeMatch(1).id);
  await expect(focused).toBeFocused();
  await expect(card.locator(".admin-live-score")).toHaveClass(/match-score--updated/);
  await expect(card.getByRole("button", { name: /Anular gol de Mandante 1/i })).toBeEnabled();
  expect(await page.evaluate(() => window.scrollY)).toBe(300);
  expect(await page.evaluate(() => (window as typeof window & { matchListBecameEmpty?: boolean }).matchListBecameEmpty)).toBe(false);
});

test("usuário mantém páginas carregadas e os mesmos cards durante o polling", async ({ page }) => {
  await page.emulateMedia({ reducedMotion: "reduce" });
  await installSession(page);
  const matches = Array.from({ length: 13 }, (_, index) => makeMatch(index + 1));
  let pageZeroCalls = 0;
  await page.route("**/api/**", (route) => {
    const url = new URL(route.request().url());
    if (url.pathname === "/api/auth/me") return fulfillSession(route);
    if (url.pathname === "/api/wallet/me") return route.fulfill({ json: { userId: "user-1", walletId: "wallet-1", balanceCents: 5000 } });
    if (url.pathname === "/api/partidas/catalog") {
      const requestedPage = Number(url.searchParams.get("page"));
      if (requestedPage === 0) pageZeroCalls += 1;
      const items = requestedPage === 0
        ? matches.slice(0, 12).map((match, index) => index === 0 && pageZeroCalls > 1 ? { ...match, teamAwayScore: 1 } : match)
        : matches.slice(12);
      return route.fulfill({ json: { items, page: requestedPage, size: 12, total: matches.length } });
    }
    return route.fulfill({ status: 404, json: {} });
  });
  await page.goto("/partidas");
  await page.getByRole("button", { name: "CARREGAR MAIS" }).click();
  await expect(page.locator("[data-match-id]")).toHaveCount(13);
  const first = page.locator(`[data-match-id="${matches[0].id}"]`);
  const last = page.locator(`[data-match-id="${matches[12].id}"]`);
  await first.evaluate((element) => element.setAttribute("data-identity", "first"));
  await last.evaluate((element) => element.setAttribute("data-identity", "last"));
  await first.evaluate((element) => {
    element.tabIndex = -1; element.focus();
    (window as typeof window & { userListBecameEmpty?: boolean }).userListBecameEmpty = false;
    const list = element.parentElement!;
    new MutationObserver(() => {
      if (list.childElementCount === 0) (window as typeof window & { userListBecameEmpty?: boolean }).userListBecameEmpty = true;
    }).observe(list, { childList: true });
    document.body.style.minHeight = "3000px"; window.scrollTo(0, 350);
  });

  await expect(first.locator(".user-match-card__teams b")).toHaveText("0 × 1", { timeout: 7_000 });
  await expect(page.locator("[data-match-id]")).toHaveCount(13);
  await expect(first).toHaveAttribute("data-identity", "first");
  await expect(last).toHaveAttribute("data-identity", "last");
  await expect(first).toBeFocused();
  await expect(first.locator(".user-match-card__teams b")).toHaveClass(/match-score--updated/);
  await expect(first.locator(".user-match-card__teams b")).toHaveCSS("animation-name", "none");
  await expect(page.locator("[data-match-state]")).toBeHidden();
  expect(await page.evaluate(() => window.scrollY)).toBe(350);
  expect(await page.evaluate(() => (window as typeof window & { userListBecameEmpty?: boolean }).userListBecameEmpty)).toBe(false);
});

test("polling não sobrepõe ciclos lentos", async ({ page }) => {
  await installSession(page);
  let active = 0;
  let maxActive = 0;
  let calls = 0;
  await page.route("**/api/**", async (route) => {
    const url = new URL(route.request().url());
    if (url.pathname === "/api/auth/me") return fulfillSession(route, true);
    if (url.pathname === "/api/admin/partidas") {
      calls += 1;
      active += 1;
      maxActive = Math.max(maxActive, active);
      if (calls > 1) await new Promise((resolve) => setTimeout(resolve, 5_500));
      active -= 1;
      return route.fulfill({ json: { items: [makeMatch(1)], page: 0, size: 50, total: 1 } });
    }
    return route.fulfill({ status: 404, json: {} });
  });
  await page.goto("/admin/partidas");
  await expect.poll(() => calls, { timeout: 12_000 }).toBeGreaterThanOrEqual(2);
  await page.waitForTimeout(5_200);
  expect(maxActive).toBe(1);
});

test("carregar mais tem prioridade sobre polling pendente", async ({ page }) => {
  await installSession(page);
  const matches = Array.from({ length: 13 }, (_, index) => makeMatch(index + 1));
  let calls = 0;
  await page.route("**/api/**", async (route) => {
    const url = new URL(route.request().url());
    if (url.pathname === "/api/auth/me") return fulfillSession(route);
    if (url.pathname === "/api/wallet/me") return route.fulfill({ json: { userId: "user-1", walletId: "wallet-1", balanceCents: 5000 } });
    if (url.pathname === "/api/partidas/catalog") {
      calls += 1;
      if (calls === 2) await new Promise((resolve) => setTimeout(resolve, 1_500));
      const requestedPage = Number(url.searchParams.get("page"));
      return route.fulfill({ json: { items: requestedPage === 0 ? matches.slice(0, 12) : matches.slice(12), page: requestedPage, size: 12, total: 13 } });
    }
    return route.fulfill({ status: 404, json: {} });
  });
  await page.goto("/partidas");
  await expect(page.locator("[data-match-id]")).toHaveCount(12);
  await expect.poll(() => calls, { timeout: 7_000 }).toBeGreaterThanOrEqual(2);
  await page.getByRole("button", { name: "CARREGAR MAIS" }).click();
  await expect(page.locator("[data-match-id]")).toHaveCount(13, { timeout: 5_000 });
});

test("falha temporária preserva a lista e o aviso some após recuperar", async ({ page }) => {
  await installSession(page);
  let calls = 0;
  await page.route("**/api/**", (route) => {
    const url = new URL(route.request().url());
    if (url.pathname === "/api/auth/me") return fulfillSession(route, true);
    if (url.pathname === "/api/admin/partidas") {
      calls += 1;
      if (calls === 2) return route.fulfill({ status: 503, json: {} });
      return route.fulfill({ json: { items: [makeMatch(1)], page: 0, size: 50, total: 1 } });
    }
    return route.fulfill({ status: 404, json: {} });
  });
  await page.goto("/admin/partidas");
  const card = page.locator("[data-match-id]");
  await expect(card).toHaveCount(1);
  await card.evaluate((element) => element.setAttribute("data-identity", "preserved"));

  const warning = page.locator("[data-match-update-status]");
  await expect(warning).toBeVisible({ timeout: 7_000 });
  await expect(card).toHaveAttribute("data-identity", "preserved");
  await expect(warning).toBeHidden({ timeout: 7_000 });
  await expect(card).toHaveAttribute("data-identity", "preserved");
});

test("falha temporária no usuário mantém cards e recupera no próximo ciclo", async ({ page }) => {
  await installSession(page);
  let calls = 0;
  await page.route("**/api/**", (route) => {
    const url = new URL(route.request().url());
    if (url.pathname === "/api/auth/me") return fulfillSession(route);
    if (url.pathname === "/api/wallet/me") return route.fulfill({ json: { userId: "user-1", walletId: "wallet-1", balanceCents: 5000 } });
    if (url.pathname === "/api/partidas/catalog") {
      calls += 1;
      if (calls === 2) return route.fulfill({ status: 503, json: {} });
      return route.fulfill({ json: { items: [makeMatch(1)], page: 0, size: 12, total: 1 } });
    }
    return route.fulfill({ status: 404, json: {} });
  });
  await page.goto("/partidas");
  const card = page.locator("[data-match-id]");
  await expect(card).toHaveCount(1);
  await card.evaluate((element) => element.setAttribute("data-identity", "preserved"));

  const warning = page.locator("[data-match-state]");
  await expect(warning).toContainText("Não foi possível atualizar", { timeout: 7_000 });
  await expect(card).toHaveAttribute("data-identity", "preserved");
  await expect(warning).toBeHidden({ timeout: 7_000 });
  await expect(card).toHaveAttribute("data-identity", "preserved");
});
