import { Page } from '@playwright/test';

import { expect, test } from './fixtures';

import {
  DEMO_PASSWORD,
  SEED,
  keepPreferencesLocal,
  seriousViolations,
  signIn,
  signInAsSeed,
} from './helpers';

const PHONE = { width: 360, height: 780 };
const DESKTOP = { width: 1280, height: 900 };

function aurora(page: Page): Promise<{ display: string; animation: string }> {
  return page.getByTestId('aurora').evaluate((element) => ({
    display: getComputedStyle(element).display,
    animation: getComputedStyle(element.firstElementChild!).animationName,
  }));
}

test.describe('shell', () => {
  test('ac3_1 a wide window shows the side menu with the current section marked', async ({
    page,
  }) => {
    await page.setViewportSize(DESKTOP);
    await signInAsSeed(page, SEED.employee);
    await page.getByTestId('nav-awards').click();

    await expect(page.getByTestId('side-nav')).toBeVisible();
    await expect(page.getByTestId('nav-awards')).toHaveAttribute('aria-current', 'page');
    await expect(page.getByTestId('nav-home')).toHaveCount(0);
    await expect(page.getByTestId('nav-reviews')).toHaveCount(0);
    await expect(page.getByTestId('nav-toggle')).toHaveCount(0);
    await expect(page.getByTestId('new-award')).toHaveCount(0);
    await expect(page.getByTestId('palette-open')).toBeVisible();
  });

  for (const [account, home, section] of [
    [SEED.admin, '/admin/users', 'nav-users'],
    [SEED.gdpr, '/compliance', 'nav-compliance'],
  ] as const) {
    test(`9.4.6 ac1 ac2 ${account} starts on ${home} without personal sections`, async ({
      page,
    }) => {
      await page.setViewportSize(DESKTOP);
      await signInAsSeed(page, account);
      await page.goto('/');

      await expect(page).toHaveURL(new RegExp(`${home}$`));
      await expect(page.getByTestId(section)).toBeVisible();
      for (const hidden of ['nav-home', 'nav-awards', 'nav-dashboard']) {
        await expect(page.getByTestId(hidden)).toHaveCount(0);
      }
      await page.goto('/dashboard');
      await expect(page).toHaveURL(/\/forbidden$/);
    });
  }

  for (const [account, home, size] of [
    [SEED.employee, '/dashboard', DESKTOP],
    [SEED.dean, '/hall-of-fame', PHONE],
    [SEED.admin, '/admin/users', DESKTOP],
    [SEED.gdpr, '/compliance', PHONE],
  ] as const) {
    test(`9.4.8 ac8_1 ac8_2 the logo takes ${account} home to ${home}`, async ({ page }) => {
      await page.setViewportSize(size);
      await signInAsSeed(page, account);
      await page.goto('/achievements');
      if (size === PHONE) {
        await page.getByTestId('nav-toggle').click();
      }

      await page.getByTestId('home-logo').click();

      await expect(page).toHaveURL(new RegExp(`${home}$`));
      if (size === PHONE) {
        await expect(page.getByTestId('side-nav')).toBeHidden();
        await expect(page.locator('.mat-drawer-backdrop.mat-drawer-shown')).toHaveCount(0);
      }
    });
  }

  test('ac3_2 a phone shows the menu button, the page title and the new award button', async ({
    page,
  }) => {
    await page.setViewportSize(PHONE);
    await page.emulateMedia({ reducedMotion: 'reduce' });
    await signIn(page, SEED.employee, DEMO_PASSWORD);
    await expect(page.getByTestId('nav-toggle')).toBeVisible();
    await page.goto('/awards');

    await expect(page.getByTestId('page-title')).toHaveText('Мої нагороди');
    await expect(page.getByTestId('nav-awards')).toBeHidden();
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(
      PHONE.width,
    );
    expect(await seriousViolations(page)).toEqual([]);

    await page.getByTestId('nav-toggle').click();
    await expect(page.getByTestId('nav-awards')).toBeVisible();
    await expect(page.getByTestId('nav-achievements')).toBeVisible();
    await page.getByTestId('nav-awards').click();
    await expect(page.getByTestId('nav-awards')).toBeHidden();

    await page.getByTestId('new-award').click();
    await expect(page).toHaveURL(/\/awards\/new$/);
  });

  test('ac3_3 the scheme switch keeps the choice and shows the aurora only in the dark', async ({
    page,
  }) => {
    await page.setViewportSize(DESKTOP);
    await page.emulateMedia({ colorScheme: 'light', reducedMotion: 'no-preference' });
    await keepPreferencesLocal(page);
    await signInAsSeed(page, SEED.employee);
    expect((await aurora(page)).display).toBe('none');

    await page.getByTestId('color-scheme-menu').click();
    await page.getByTestId('color-scheme-dark').click();
    await expect(page.locator('html')).toHaveAttribute('data-color-scheme', 'dark');
    const moving = await aurora(page);
    expect(moving.display).toBe('block');
    expect(moving.animation).toContain('shell-aurora');
    expect(await seriousViolations(page)).toEqual([]);

    await page.reload();
    await expect(page.getByTestId('nav-awards')).toBeVisible();
    await expect(page.locator('html')).toHaveAttribute('data-color-scheme', 'dark');

    await page.emulateMedia({ reducedMotion: 'reduce' });
    expect(await aurora(page)).toEqual({ display: 'block', animation: 'none' });

    await page.getByTestId('color-scheme-menu').click();
    await page.getByTestId('color-scheme-system').click();
    await expect(page.locator('html')).not.toHaveAttribute('data-color-scheme', /.+/);
    expect((await aurora(page)).display).toBe('none');
  });
});
