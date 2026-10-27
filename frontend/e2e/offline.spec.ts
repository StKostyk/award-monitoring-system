import { expect, test } from './fixtures';

import { SEED, seriousViolations, signInAsSeed } from './helpers';

const { employee } = SEED;
const NOTICE =
  "Немає з'єднання з сервером. Сторінки, яким потрібні дані, відкриються, щойно з'єднання відновиться.";
const RECONNECT_MS = 2000;

test.describe('connection loss', () => {
  test('ac1_4 registers no service worker in the development build', async ({ page }) => {
    await signInAsSeed(page, employee);

    const registrations = await page.evaluate(async () =>
      'serviceWorker' in navigator ? (await navigator.serviceWorker.getRegistrations()).length : 0,
    );

    expect(registrations).toBe(0);
  });

  test('ac1_7 ac1_9 shows the offline notice while the connection is lost', async ({
    page,
    context,
  }) => {
    await signInAsSeed(page, employee);
    await expect(page.getByTestId('offline-notice')).toHaveCount(0);

    await context.setOffline(true);
    await expect(page.getByTestId('offline-notice')).toContainText(NOTICE);
    await expect(page.getByTestId('nav-awards')).toBeVisible();
    expect(await seriousViolations(page)).toEqual([]);

    await context.setOffline(false);
    await expect(page.getByTestId('offline-notice')).toHaveCount(0, { timeout: RECONNECT_MS });
  });

  test('ac1_8 opens the offline page instead of the sign-in and signs in once online', async ({
    page,
    context,
  }) => {
    await page.goto('/public/achievements');
    await expect(page.getByTestId('login')).toBeVisible();

    await context.setOffline(true);
    await page.getByTestId('login').click();
    await expect(page).toHaveURL(/\/offline\?next=/);
    await expect(page.getByTestId('offline-card')).toContainText("Немає з'єднання");
    await expect(page.getByTestId('offline-retry')).toHaveText('Спробувати ще раз');
    expect(await seriousViolations(page)).toEqual([]);

    await context.setOffline(false);
    await expect(page).toHaveURL(/localhost:8080\/login/);
  });

  test('ac1_12 signs out on the device without a connection', async ({ page, context }) => {
    await signInAsSeed(page, employee);

    await context.setOffline(true);
    await page.getByTestId('logout').click();

    await expect(page).toHaveURL(/\/offline\?next=%2F/);
    expect(
      await page.evaluate(() =>
        Object.keys(sessionStorage).filter((key) => /access_token|refresh_token/.test(key)),
      ),
    ).toEqual([]);
    await context.setOffline(false);
  });

  test('ac1_1 ac1_2 serves the manifests and icons of both brands', async ({ page, request }) => {
    for (const [path, name] of [
      ['/brand/manifest.webmanifest', 'Облік нагород ЧНУ'],
      ['/brand/neutral/manifest.webmanifest', 'Облік нагород'],
    ]) {
      const manifest = await (await request.get(path)).json();
      expect(manifest).toEqual(
        expect.objectContaining({
          name,
          lang: 'uk',
          start_url: '/',
          scope: '/',
          display: 'standalone',
        }),
      );
      expect(manifest.orientation).toBeUndefined();
      expect(manifest.icons.map((icon: { purpose: string }) => icon.purpose)).toEqual([
        'any',
        'any',
        'maskable',
      ]);
      const folder = path.replace('manifest.webmanifest', 'icons/');
      for (const icon of [
        'icon-192.png',
        'icon-512.png',
        'icon-maskable-512.png',
        'apple-touch-icon.png',
      ]) {
        const answer = await request.get(folder + icon);
        expect(answer.headers()['content-type'], folder + icon).toBe('image/png');
      }
    }
    await page.goto('/public/achievements');
    await expect(page.locator('link[rel="manifest"]')).toHaveAttribute(
      'href',
      'brand/manifest.webmanifest',
    );
    await expect(page.locator('link[rel="apple-touch-icon"]')).toHaveCount(1);
    await expect(page.locator('meta[name="theme-color"]')).toHaveAttribute('content', '#0047AB');
  });
});
