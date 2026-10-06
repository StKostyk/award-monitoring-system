import { expect, test } from '@playwright/test';

import {
  DEMO_PASSWORD,
  SEED,
  background,
  seriousViolations,
  signIn,
  signInAsSeed,
} from './helpers';

const { employee } = SEED;
const phoneWidth = 400;
const phoneHeight = 800;
const neutralBrand = {
  id: 'neutral',
  name: { uk: 'Облік нагород', en: 'Award Registry' },
  organization: { uk: 'Університет', en: 'University' },
  logo: 'brand/neutral/logo.svg',
};
const screens = ['/', '/awards', '/awards/new'];

test.describe('brand theme', () => {
  test('ac1 ac3 ac7 applies the ChNU brand with self-hosted fonts', async ({ page }) => {
    const external: string[] = [];
    page.on('request', (request) => {
      if (/fonts\.(googleapis|gstatic)\.com/.test(request.url())) {
        external.push(request.url());
      }
    });
    await signInAsSeed(page, employee);

    await expect(page.locator('html')).toHaveClass(/brand-chnu/);
    await expect(page).toHaveTitle('Облік нагород ЧНУ');
    await expect(page.getByTestId('side-nav')).toContainText(
      'Чернівецький національний університет',
    );
    expect(await background(page, 'mat-sidenav')).toBe('rgb(0, 50, 120)');
    expect(
      await page.locator('body').evaluate((body) => getComputedStyle(body).fontFamily),
    ).toContain('Nunito');
    await expect
      .poll(() => page.evaluate(() => document.fonts.check('16px Nunito', 'Облік')))
      .toBe(true);

    await page.getByTestId('language-toggle').click();
    await expect(page).toHaveTitle('ChNU Awards');
    await page.getByTestId('language-toggle').click();
    expect(external).toEqual([]);
  });

  test('ac2 falls back to the neutral brand without a brand file', async ({ page }) => {
    await page.route('**/brand/brand.json', (route) => route.fulfill({ status: 404, body: '' }));
    await signInAsSeed(page, employee);

    await expect(page.locator('html')).toHaveClass(/brand-neutral/);
    await expect(page).toHaveTitle('Облік нагород');
    expect(await background(page, 'mat-sidenav')).toBe('rgb(30, 61, 48)');
  });

  test('ac4 follows the device colour scheme and keeps the chosen one', async ({ page }) => {
    await page.emulateMedia({ colorScheme: 'dark' });
    await signInAsSeed(page, employee);

    expect(await background(page, 'body')).toBe('rgb(31, 31, 31)');

    await page.getByTestId('user-menu').click();
    await page.getByTestId('color-scheme-menu').click();
    await page.getByTestId('color-scheme-light').click();
    await expect(page.locator('html')).toHaveAttribute('data-color-scheme', 'light');
    expect(await background(page, 'body')).toBe('rgb(249, 248, 255)');

    await page.reload();
    await expect(page.getByTestId('nav-awards')).toBeVisible();
    expect(await background(page, 'body')).toBe('rgb(249, 248, 255)');

    await page.getByTestId('user-menu').click();
    await page.getByTestId('color-scheme-menu').click();
    await page.getByTestId('color-scheme-system').click();
    expect(await background(page, 'body')).toBe('rgb(31, 31, 31)');
  });

  test('ac5 opens the menu as a drawer on a phone', async ({ page }) => {
    await page.setViewportSize({ width: phoneWidth, height: phoneHeight });
    await signIn(page, employee, DEMO_PASSWORD);
    await expect(page.getByTestId('nav-toggle')).toBeVisible();
    await expect(page.getByTestId('nav-awards')).toBeHidden();
    await expect(page.getByTestId('brand')).toBeVisible();

    await page.getByTestId('nav-toggle').click();
    await page.getByTestId('nav-awards').click();

    await expect(page).toHaveURL(/\/awards$/);
    await expect(page.getByTestId('nav-awards')).toBeHidden();

    await page.getByTestId('nav-toggle').click();
    await page.getByTestId('nav-awards').click();
    await expect(page.getByTestId('nav-awards')).toBeHidden();

    await expect(page.getByTestId('award-item').first()).toBeVisible();
    await page.mouse.move(phoneWidth / 2, phoneHeight / 2);
    await page.mouse.wheel(0, phoneHeight * 3);
    await expect(page.getByTestId('award-item').first()).not.toBeInViewport();
    await expect(page.getByTestId('nav-toggle')).toBeInViewport();
    await expect(page.getByTestId('user-menu')).toBeVisible();
    await expect(page.getByTestId('logout')).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(
      phoneWidth,
    );
    await page.waitForLoadState('networkidle');
    expect(await seriousViolations(page)).toEqual([]);
  });

  for (const brand of ['chnu', 'neutral']) {
    for (const scheme of ['light', 'dark'] as const) {
      test(`ac6 has no serious accessibility violations in the ${brand} brand, ${scheme}`, async ({
        page,
      }) => {
        if (brand === 'neutral') {
          await page.route('**/brand/brand.json', (route) => route.fulfill({ json: neutralBrand }));
        }
        await page.emulateMedia({ colorScheme: scheme });
        await signInAsSeed(page, employee);
        await expect(page.locator('html')).toHaveClass(new RegExp(`brand-${brand}`));

        for (const screen of screens) {
          await page.goto(screen);
          await expect(page.getByTestId('nav-awards')).toBeVisible();
          await page.waitForLoadState('networkidle');
          expect(await seriousViolations(page), screen).toEqual([]);
        }
      });
    }
  }
});
