import AxeBuilder from '@axe-core/playwright';
import { Page, expect, test } from '@playwright/test';

const phoneWidth = 360;
const phoneHeight = 740;
const minimumTarget = 44;

async function openLogin(page: Page): Promise<void> {
  await page.goto('/');
  await expect(page).toHaveURL(/localhost:8080\/login/);
  await expect(page.getByTestId('login-brand')).toBeVisible();
}

function background(page: Page, selector: string): Promise<string> {
  return page
    .locator(selector)
    .first()
    .evaluate((element) => getComputedStyle(element).backgroundColor);
}

async function seriousViolations(page: Page): Promise<string[]> {
  const result = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21aa'])
    .analyze();
  return result.violations
    .filter((violation) => violation.impact === 'serious' || violation.impact === 'critical')
    .map(
      (violation) => `${violation.id}: ${violation.nodes.map((node) => node.target).join(' | ')}`,
    );
}

test.describe('sign-in page brand', () => {
  test('ac1 ac4 shows the ChNU brand with its own fonts and no other host', async ({ page }) => {
    const external: string[] = [];
    page.on('request', (request) => {
      if (!/^http:\/\/localhost(:\d+)?\//.test(request.url())) {
        external.push(request.url());
      }
    });
    await openLogin(page);

    await expect(page.getByTestId('login-brand')).toContainText('Облік нагород ЧНУ');
    await expect(page.getByTestId('login-brand')).toContainText(
      'Чернівецький національний університет',
    );
    expect(await background(page, '.brand')).toBe('rgb(0, 50, 120)');
    expect(await background(page, 'button[type="submit"]')).toBe('rgb(0, 71, 171)');
    await expect
      .poll(() => page.evaluate(() => document.fonts.check('16px Nunito', 'Вхід')))
      .toBe(true);

    await page.getByRole('link', { name: 'English' }).click();
    await expect(page.getByTestId('login-brand')).toContainText('ChNU Awards');
    expect(external).toEqual([]);
  });

  for (const scheme of ['light', 'dark'] as const) {
    test(`ac2 ac3 fits a phone and passes axe, ${scheme}`, async ({ page }) => {
      await page.setViewportSize({ width: phoneWidth, height: phoneHeight });
      await page.emulateMedia({ colorScheme: scheme });
      await openLogin(page);

      expect(await background(page, 'body')).toBe(
        scheme === 'dark' ? 'rgb(31, 31, 31)' : 'rgb(249, 248, 255)',
      );
      expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(
        phoneWidth,
      );
      const heights = await page
        .locator('main a, main button, main input:not([type="hidden"])')
        .evaluateAll((elements) =>
          elements.map((element) => element.getBoundingClientRect().height),
        );
      expect(Math.min(...heights)).toBeGreaterThanOrEqual(minimumTarget);
      expect(await seriousViolations(page)).toEqual([]);

      await page.fill('#username', 'nobody@chnu.edu.ua');
      await page.fill('#password', 'wrong-password');
      await page.click('button[type="submit"]');
      await expect(page.getByRole('alert')).toBeVisible();
      expect(await seriousViolations(page)).toEqual([]);
    });
  }
});
