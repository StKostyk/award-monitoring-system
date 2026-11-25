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
  await expect(page.getByTestId('login-form')).toBeVisible();
}

async function brandSitsAboveContent(page: Page, brand: string, content: string): Promise<void> {
  const brandBox = await page.locator(brand).boundingBox();
  const contentBox = await page.locator(content).first().boundingBox();
  expect(brandBox!.y + brandBox!.height).toBeLessThanOrEqual(contentBox!.y);
}

async function noSidewaysScroll(page: Page, width: number): Promise<void> {
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(
    width,
  );
}

test.describe('sign-in page themes', () => {
  test('ac1_1 ac1_5 shows the catalogue cards with the quote of the day and the ChNU brand', async ({
    page,
  }) => {
    const external: string[] = [];
    page.on('request', (request) => {
      if (!/^http:\/\/localhost(:\d+)?\//.test(request.url())) {
        external.push(request.url());
      }
    });
    await page.setViewportSize({ width: 1280, height: 800 });
    await page.emulateMedia({ colorScheme: 'light' });
    await openLogin(page);

    await expect(page.getByTestId('login-brand')).toBeVisible();
    await expect(page.locator('.catalogue-card--today').first()).toContainText('ЦИТАТА ДНЯ');
    await expect(page.locator('h1')).toHaveText('Ваша картка в цьому каталозі вже чекає.');
    await expect(page.getByText('Каталог досягнень університету')).toBeVisible();
    await expect(page.getByTestId('signin-brand')).toContainText('Облік нагород ЧНУ');
    await expect(page.locator('.signin-footer')).toContainText(
      'Чернівецький національний університет',
    );
    await expect(page.locator('.signin-footer a')).toHaveAttribute('href', /\/privacy$/);
    const quote = await page.getByTestId('quote-of-the-day').textContent();
    expect(await background(page, 'button[type="submit"]')).toBe('rgb(122, 31, 46)');
    await expect
      .poll(() => page.evaluate(() => document.fonts.check('800 16px "Manrope Variable"', 'Вхід')))
      .toBe(true);
    await noSidewaysScroll(page, 1280);

    await page.reload();
    expect(await page.getByTestId('quote-of-the-day').textContent()).toBe(quote);
    await page.getByRole('link', { name: 'EN', exact: true }).click();
    await expect(page.getByTestId('quote-of-the-day')).toContainText('Quote of the day');
    await expect(page.getByTestId('signin-brand')).toContainText('ChNU Awards');
    await expect(page.getByTestId('login-award-count')).toHaveCount(0);
    expect(external).toEqual([]);
  });

  test('ac1_8 the moving cards stop under reduced motion', async ({ page }) => {
    await page.emulateMedia({ reducedMotion: 'reduce' });
    await openLogin(page);

    const animations = await page
      .locator('.catalogue-stream__track')
      .evaluateAll((tracks) => tracks.map((track) => getComputedStyle(track).animationName));
    expect(animations).toEqual(['none', 'none']);
  });

  for (const scheme of ['light', 'dark'] as const) {
    test(`ac1_6 fits a phone and passes axe, ${scheme}`, async ({ page }) => {
      await page.setViewportSize({ width: phoneWidth, height: phoneHeight });
      await page.emulateMedia({ colorScheme: scheme });
      await openLogin(page);

      expect(await background(page, 'body')).toBe(
        scheme === 'dark' ? 'rgb(11, 16, 32)' : 'rgb(247, 241, 233)',
      );
      const band = await page.getByTestId('login-brand').boundingBox();
      expect(band!.height).toBeLessThanOrEqual(phoneHeight / 3);
      await brandSitsAboveContent(page, '[data-testid="login-brand"]', 'main');
      await noSidewaysScroll(page, phoneWidth);
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
      await brandSitsAboveContent(page, '[data-testid="login-brand"]', 'main');
      await noSidewaysScroll(page, phoneWidth);
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
