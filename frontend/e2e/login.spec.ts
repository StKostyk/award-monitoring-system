import { Page, expect, test } from './fixtures';

import { background, seriousViolations } from './helpers';

const phoneWidth = 360;
const phoneHeight = 740;
const minimumTarget = 44;
const accountPages = [
  '/register',
  '/registration-pending',
  '/verify-email',
  '/forgot-password',
  '/reset-password',
  '/confirm-email-change',
];

async function openLogin(page: Page): Promise<void> {
  await page.goto('/');
  await expect(page).toHaveURL(/localhost:8080\/login/);
  await expect(page.getByTestId('login-brand')).toBeVisible();
}

async function brandSitsAboveContent(page: Page, brand: string, content: string): Promise<void> {
  const brandBox = await page.locator(brand).boundingBox();
  const contentBox = await page.locator(content).first().boundingBox();
  expect(brandBox!.y + brandBox!.height).toBeLessThanOrEqual(contentBox!.y);
}

test.describe('sign-in page brand', () => {
  test('ac4_1 shows the ChNU brand, the count and the claim in the new look', async ({ page }) => {
    const external: string[] = [];
    page.on('request', (request) => {
      if (!/^http:\/\/localhost(:\d+)?\//.test(request.url())) {
        external.push(request.url());
      }
    });
    await page.emulateMedia({ colorScheme: 'light' });
    await openLogin(page);

    const brand = page.getByTestId('login-brand');
    await expect(brand).toContainText('Облік нагород ЧНУ');
    await expect(brand).toContainText('Чернівецький національний університет');
    await expect(page.getByTestId('login-award-count')).toHaveText(/^\d[\d\s]* нагород[аи]?$/);
    await expect(brand).toContainText('Нагороди університету');
    expect(await background(page, '.brand')).toBe('rgb(94, 22, 33)');
    expect(await background(page, 'button[type="submit"]')).toBe('rgb(122, 31, 43)');
    await expect
      .poll(() => page.evaluate(() => document.fonts.check('800 16px "Manrope Variable"', 'Вхід')))
      .toBe(true);
    await expect
      .poll(() => page.evaluate(() => document.fonts.check('16px "Onest Variable"', 'Пароль')))
      .toBe(true);

    await page.getByRole('link', { name: 'English' }).click();
    await expect(brand).toContainText('ChNU Awards');
    await expect(page.getByTestId('login-award-count')).toHaveText(/^\d[\d,]* awards?$/);
    expect(external).toEqual([]);
  });

  for (const scheme of ['light', 'dark'] as const) {
    test(`ac4_4 fits a phone and passes axe, ${scheme}`, async ({ page }) => {
      await page.setViewportSize({ width: phoneWidth, height: phoneHeight });
      await page.emulateMedia({ colorScheme: scheme });
      await openLogin(page);

      expect(await background(page, 'body')).toBe(
        scheme === 'dark' ? 'rgb(6, 9, 20)' : 'rgb(246, 239, 228)',
      );
      await brandSitsAboveContent(page, '.brand', 'main');
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
      await brandSitsAboveContent(page, '.brand', 'main');
      expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(
        phoneWidth,
      );
      expect(await seriousViolations(page)).toEqual([]);
    });
  }
});

test.describe('account pages brand panel', () => {
  for (const path of accountPages) {
    test(`ac4_3 ${path} carries the brand panel beside the form`, async ({ page }) => {
      await page.goto(path);

      const brand = page.getByTestId('auth-brand');
      await expect(brand).toContainText('Облік нагород ЧНУ');
      await expect(page.getByTestId('auth-claim')).toContainText('Нагороди університету');
      const brandBox = await brand.boundingBox();
      const cardBox = await page.locator('.auth-card').boundingBox();
      expect(brandBox!.x + brandBox!.width).toBeLessThanOrEqual(cardBox!.x);
    });
  }

  for (const scheme of ['light', 'dark'] as const) {
    test(`ac4_3 ac4_4 the register page shows the strip on a phone, ${scheme}`, async ({
      page,
    }) => {
      await page.setViewportSize({ width: phoneWidth, height: phoneHeight });
      await page.emulateMedia({ colorScheme: scheme });
      await page.goto('/register');

      await expect(page.getByTestId('auth-brand')).toBeVisible();
      await brandSitsAboveContent(page, '[data-testid="auth-brand"]', '.auth-card');
      expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(
        phoneWidth,
      );
      expect(await seriousViolations(page)).toEqual([]);
    });
  }
});
