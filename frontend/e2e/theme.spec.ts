import { Page } from '@playwright/test';

import { expect, test } from './fixtures';

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

/** The computed colour a token resolves to under the current colour scheme. */
async function token(page: Page, name: string): Promise<string> {
  return page.evaluate((property) => {
    const probe = document.createElement('div');
    probe.style.color = `var(${property})`;
    document.body.appendChild(probe);
    const color = getComputedStyle(probe).color;
    probe.remove();
    return color;
  }, name);
}

test.describe('brand theme', () => {
  test('ac1 ac3 ac7 ac1_1 ac1_3 applies the ChNU brand with self-hosted fonts', async ({
    page,
  }) => {
    const external: string[] = [];
    page.on('request', (request) => {
      const foreignFont =
        request.resourceType() === 'font' &&
        new URL(request.url()).origin !== new URL(page.url()).origin;
      if (foreignFont || /fonts\.(googleapis|gstatic)\.com/.test(request.url())) {
        external.push(request.url());
      }
    });
    await page.emulateMedia({ colorScheme: 'light' });
    await signInAsSeed(page, employee);

    await expect(page.locator('html')).toHaveClass(/brand-chnu/);
    await expect(page).toHaveTitle('Головна — Облік нагород ЧНУ');
    await expect(page.getByTestId('side-nav')).toContainText(
      'Чернівецький національний університет',
    );
    expect(await background(page, 'mat-sidenav')).toBe('rgb(94, 22, 33)');
    expect(await background(page, 'body')).toBe('rgb(246, 239, 228)');
    expect(await token(page, '--mat-sys-primary')).toBe('rgb(122, 31, 43)');
    expect(
      await page.locator('body').evaluate((body) => getComputedStyle(body).fontFamily),
    ).toContain('Onest Variable');
    expect(
      await page.evaluate(() => {
        const heading = document.createElement('h2');
        document.querySelector('main')?.appendChild(heading);
        const family = getComputedStyle(heading).fontFamily;
        heading.remove();
        return family;
      }),
    ).toContain('Manrope Variable');
    await expect
      .poll(() =>
        page.evaluate(() =>
          [...document.fonts]
            .filter((face) => face.status === 'loaded')
            .map((face) => face.family.replaceAll('"', '')),
        ),
      )
      .toEqual(expect.arrayContaining(['Onest Variable', 'Manrope Variable']));

    await page.getByTestId('language-toggle').click();
    await expect(page).toHaveTitle('Home — ChNU Awards');
    await page.getByTestId('language-toggle').click();
    expect(external).toEqual([]);
  });

  test('ac2 falls back to the neutral brand without a brand file', async ({ page }) => {
    await page.route('**/brand/brand.json', (route) => route.fulfill({ status: 404, body: '' }));
    await signInAsSeed(page, employee);

    await expect(page.locator('html')).toHaveClass(/brand-neutral/);
    await expect(page).toHaveTitle('Головна — Облік нагород');
    expect(await background(page, 'mat-sidenav')).toBe('rgb(30, 61, 48)');
  });

  for (const scheme of ['light', 'dark'] as const) {
    test(`ac1_2 applies the neutral brand for an unknown id, ${scheme}`, async ({ page }) => {
      await page.route('**/brand/brand.json', (route) =>
        route.fulfill({ json: { ...neutralBrand, id: 'unknown' } }),
      );
      await page.emulateMedia({ colorScheme: scheme });
      await signInAsSeed(page, employee);

      await expect(page.locator('html')).toHaveClass(/brand-neutral/);
      expect(await token(page, '--mat-sys-primary')).toBe(
        scheme === 'light' ? 'rgb(45, 106, 79)' : 'rgb(143, 213, 176)',
      );
      expect(
        await page.locator('body').evaluate((body) => getComputedStyle(body).fontFamily),
      ).toContain('Onest Variable');
    });
  }

  test('ac1_1 uses the aurora scheme of the ChNU brand in the dark', async ({ page }) => {
    await page.emulateMedia({ colorScheme: 'dark' });
    await signInAsSeed(page, employee);

    expect(await background(page, 'body')).toBe('rgb(6, 9, 20)');
    expect(await token(page, '--mat-sys-primary')).toBe('rgb(125, 220, 255)');
    expect(await token(page, '--app-gold')).toBe('rgb(245, 196, 81)');
  });

  test('ac1_4 draws every icon as a Lucide SVG with named icon-only controls', async ({ page }) => {
    await signInAsSeed(page, employee);

    for (const screen of screens) {
      await page.goto(screen);
      await expect(page.getByTestId('nav-awards')).toBeVisible();
      await expect(page.locator('mat-icon'), screen).toHaveCount(0);
      expect(await page.locator('app-icon').count(), screen).toBeGreaterThan(0);
      await expect(page.locator('app-icon:not(:has(svg))'), screen).toHaveCount(0);
      const unnamed = await page.evaluate(() =>
        [...document.querySelectorAll('button, a')]
          .filter((control) => control.querySelector('app-icon'))
          .filter((control) => !control.textContent?.trim())
          .filter(
            (control) =>
              !control.getAttribute('aria-label') && !control.getAttribute('aria-labelledby'),
          )
          .map((control) => control.outerHTML.slice(0, 120)),
      );
      expect(unnamed, screen).toEqual([]);
    }
  });

  test('ac4 follows the device colour scheme and keeps the chosen one', async ({ page }) => {
    await page.emulateMedia({ colorScheme: 'dark' });
    await signInAsSeed(page, employee);

    expect(await background(page, 'body')).toBe('rgb(6, 9, 20)');

    await page.getByTestId('user-menu').click();
    await page.getByTestId('color-scheme-menu').click();
    await page.getByTestId('color-scheme-light').click();
    await expect(page.locator('html')).toHaveAttribute('data-color-scheme', 'light');
    expect(await background(page, 'body')).toBe('rgb(246, 239, 228)');

    await page.reload();
    await expect(page.getByTestId('nav-awards')).toBeVisible();
    expect(await background(page, 'body')).toBe('rgb(246, 239, 228)');

    await page.getByTestId('user-menu').click();
    await page.getByTestId('color-scheme-menu').click();
    await page.getByTestId('color-scheme-system').click();
    expect(await background(page, 'body')).toBe('rgb(6, 9, 20)');
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
      test(`ac6 ac1_5 has no serious accessibility violations in the ${brand} brand, ${scheme}`, async ({
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
