import { expect, type Locator, type Page } from "@playwright/test";

export async function expectCenteredDialog(dialog: Locator) {
  await expect(dialog).toBeVisible();
  const delta = await dialog.evaluate((element) => {
    const rect = element.getBoundingClientRect();
    return {
      x: Math.abs(rect.left + rect.width / 2 - window.innerWidth / 2),
      y: Math.abs(rect.top + rect.height / 2 - window.innerHeight / 2),
    };
  });
  expect(delta.x).toBeLessThanOrEqual(2);
  expect(delta.y).toBeLessThanOrEqual(2);
}

export const dialogBySelector = (page: Page, selector?: string) =>
  selector ? page.locator(selector) : page.getByRole("dialog");
