import { expect, test } from "@playwright/test";

const viewports = [
  { name: "mobile-320", width: 320, height: 568, headerHeight: 64, compact: true },
  { name: "mobile-390", width: 390, height: 844, headerHeight: 64, compact: true },
  { name: "reported-624", width: 624, height: 959, headerHeight: 64, compact: true },
  { name: "sm-640", width: 640, height: 960, headerHeight: 68, compact: true },
  { name: "md-768", width: 768, height: 1024, headerHeight: 72, compact: true },
  { name: "tablet-834", width: 834, height: 1194, headerHeight: 72, compact: true },
  { name: "desktop-1024", width: 1024, height: 960, headerHeight: 82, compact: false },
  { name: "xl-1280", width: 1280, height: 960, headerHeight: 82, compact: false },
  { name: "2xl-1536", width: 1536, height: 960, headerHeight: 82, compact: false },
] as const;

for (const route of ["login", "registro"] as const) {
  for (const viewport of viewports) {
    test(`${route} permanece responsivo em ${viewport.name}`, async ({ page }) => {
      const consoleErrors: string[] = [];
      page.on("console", (message) => {
        if (message.type() === "error") consoleErrors.push(message.text());
      });
      await page.setViewportSize({ width: viewport.width, height: viewport.height });

      const highlightsResponse = page.waitForResponse((response) => response.url().endsWith("/api/palpites/destaques"));
      await page.goto(`/${route}`, { waitUntil: "networkidle" });

      expect((await highlightsResponse).status()).toBe(204);
      await expect(page).toHaveTitle(route === "login" ? "Entrar | Bolão Boladão" : "Criar conta | Bolão Boladão");
      await expect(page.locator(".auth-panel h2")).toContainText(route === "login" ? "Entre no jogo" : "Crie sua conta");
      await expect(page.locator("[data-match-highlights]")).toBeHidden();

      const layout = await page.evaluate(() => {
        const bounds = (selector: string) => {
          const box = document.querySelector(selector)?.getBoundingClientRect();
          if (!box) throw new Error(`Elemento ausente: ${selector}`);
          return { x: box.x, y: box.y, width: box.width, height: box.height, right: box.right, bottom: box.bottom };
        };
        const intersects = (first: ReturnType<typeof bounds>, second: ReturnType<typeof bounds>) =>
          first.x < second.right && first.right > second.x && first.y < second.bottom && first.bottom > second.y;

        const header = bounds(".site-header");
        const hero = bounds(".auth-hero");
        const copy = bounds(".auth-hero__copy");
        const title = bounds(".auth-hero__copy h1");
        const description = bounds(".auth-hero__copy p");
        const features = bounds(".auth-hero__features");
        const main = bounds(".auth-page__main");
        const panel = bounds(".auth-panel");
        const titleLinesInsideViewport = Array.from(document.querySelectorAll(".auth-hero__title:not([style*='display: none']) > span"))
          .filter((line) => getComputedStyle(line.parentElement!).display !== "none")
          .every((line) => {
            const box = line.getBoundingClientRect();
            return box.left >= 0 && box.right <= window.innerWidth && line.scrollWidth <= line.clientWidth;
          });

        return {
          header,
          heroPosition: getComputedStyle(document.querySelector(".auth-hero")!).position,
          mobileTitleVisible: getComputedStyle(document.querySelector(".auth-hero__title--mobile")!).display !== "none",
          desktopTitleVisible: getComputedStyle(document.querySelector(".auth-hero__title--desktop")!).display !== "none",
          titleLinesInsideViewport,
          compactOrder: header.bottom <= hero.y + .5
            && title.bottom <= description.y + .5
            && description.bottom <= features.y + .5
            && features.bottom <= hero.bottom + .5
            && hero.bottom <= main.y + .5,
          desktopSeparation: !intersects(copy, panel) && !intersects(features, panel),
          panelCenterDelta: Math.abs((panel.x + panel.width / 2) - window.innerWidth / 2),
          panelCenter: panel.x + panel.width / 2,
          overflow: document.documentElement.scrollWidth > window.innerWidth || document.body.scrollWidth > window.innerWidth,
        };
      });

      expect(layout.header.height).toBeCloseTo(viewport.headerHeight, 0);
      expect(layout.heroPosition).toBe(viewport.compact ? "relative" : "absolute");
      expect(layout.mobileTitleVisible).toBe(viewport.compact);
      expect(layout.desktopTitleVisible).toBe(!viewport.compact);
      expect(layout.titleLinesInsideViewport).toBe(true);
      expect(viewport.compact ? layout.compactOrder : layout.desktopSeparation).toBe(true);
      expect(viewport.compact ? layout.panelCenterDelta < 1 : layout.panelCenter > viewport.width * .6).toBe(true);
      expect(layout.overflow).toBe(false);

      const action = page.locator(".site-header .button--outline");
      const actionBox = await action.boundingBox();
      expect(actionBox).not.toBeNull();
      expect(actionBox!.width).toBeGreaterThanOrEqual(44);
      expect(actionBox!.height).toBeGreaterThanOrEqual(44);

      await page.keyboard.press("Tab");
      await page.keyboard.press("Tab");
      await expect(action).toBeFocused();
      expect(await action.evaluate((element) => getComputedStyle(element).outlineWidth)).toBe("3px");

      const expectedRoute = route === "login" ? "/registro" : "/login";
      await action.click();
      await expect(page).toHaveURL(new RegExp(`${expectedRoute}/?$`));
      expect(consoleErrors).toEqual([]);
    });
  }
}

test("gateway mantém palpites público e partidas protegidas", async ({ request }) => {
  expect((await request.get("/api/palpites/destaques")).status()).toBe(204);
  expect((await request.get("/api/partidas")).status()).toBe(401);
});

test("web interpreta o status LIVE do contrato público", async ({ page }) => {
  await page.route("**/api/palpites/destaques", (route) => route.fulfill({
    contentType: "application/json",
    body: JSON.stringify({
      highlights: [{
        matchId: "match-live",
        teamHome: "Aurora",
        teamAway: "Estrela",
        start: "2026-07-14T21:30:00Z",
        status: "LIVE",
        totalStakeCents: 2500,
      }],
    }),
  }));

  await page.goto("/login", { waitUntil: "networkidle" });
  await expect(page.locator(".match-teaser__live")).toHaveText("AO VIVO");
  await expect(page.locator(".match-teaser__stake strong")).toHaveText("R$ 25,00");
});
