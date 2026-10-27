import { Page } from '@playwright/test';

import { expect, test } from './fixtures';

import { DEMO_PASSWORD, SEED } from './helpers';

/**
 * The service worker exists only in the production build: this spec runs against the Compose stack on demand,
 * `PLAYWRIGHT_BASE_URL=http://localhost npx playwright test pwa` (PRD 8.1 D-5), and is skipped otherwise.
 */
const BASE_URL = process.env['PLAYWRIGHT_BASE_URL'];
const BACKEND_PATH =
  /^\/(api|oauth2|connect|\.well-known|login|logout|error|login-assets|swagger-ui|v3\/api-docs|privacy\/|health)/;

test.skip(!BASE_URL, 'needs the production build: set PLAYWRIGHT_BASE_URL');

async function signInWithWorker(page: Page): Promise<void> {
  await page.goto('/');
  await expect(page).toHaveURL(/\/login$/);
  await page.fill('#username', SEED.employee);
  await page.fill('#password', DEMO_PASSWORD);
  await page.click('button[type="submit"]');
  await expect(page.getByTestId('nav-awards')).toBeVisible();
  await page.evaluate(async () => {
    await navigator.serviceWorker.ready;
  });
}

test.describe('installable app', () => {
  test('ac1_1 ac1_11 serves the manifest and the worker files for revalidation', async ({
    request,
  }) => {
    const manifest = await request.get('/brand/manifest.webmanifest');
    expect(manifest.headers()['content-type']).toContain('application/manifest+json');
    expect(manifest.headers()['cache-control']).toBe('no-cache');
    for (const file of ['/ngsw-worker.js', '/ngsw.json', '/safety-worker.js']) {
      const answer = await request.get(file);
      expect(answer.status(), file).toBe(200);
      expect(answer.headers()['cache-control'], file).toBe('no-cache');
      expect(answer.headers()['content-security-policy'], file).toContain("default-src 'self'");
    }
    const index = await (await request.get('/')).text();
    const bundle = /src="(main-[A-Z0-9]+\.js)"/.exec(index)?.[1];
    expect((await request.get(`/${bundle}`)).headers()['cache-control']).toContain('immutable');
  });

  test('ac1_3 ac1_5 ac1_6 caches the shell only and leaves backend pages to the network', async ({
    page,
  }) => {
    await signInWithWorker(page);
    const scope = await page.evaluate(
      async () => (await navigator.serviceWorker.getRegistration())?.scope,
    );
    expect(scope).toBe(`${BASE_URL}/`);
    for (const path of ['/awards', '/achievements', '/analytics/summary', '/profile']) {
      await page.goto(path);
    }

    const cached = await page.evaluate(async () => {
      const urls: string[] = [];
      for (const name of await caches.keys()) {
        const cache = await caches.open(name);
        urls.push(...(await cache.keys()).map((request) => new URL(request.url).pathname));
      }
      return urls;
    });
    expect(cached.filter((path) => BACKEND_PATH.test(path))).toEqual([]);

    await page.context().clearCookies();
    for (const path of ['/login', '/swagger-ui/index.html', '/privacy/1.0/uk.html']) {
      const answer = await page.goto(path);
      expect(await answer?.text(), path).not.toContain('<app-root');
    }
  });

  test('ac1_7 ac1_8 ac1_12 starts without a connection', async ({ page, context }) => {
    await signInWithWorker(page);
    await page.goto('/awards');

    await context.setOffline(true);
    const started = Date.now();
    await page.reload();
    await expect(page.getByTestId('offline-notice')).toBeVisible();
    expect(Date.now() - started).toBeLessThan(3000);
    await expect(page.getByTestId('nav-awards')).toBeVisible();
    await expect(page).toHaveURL(/\/awards$/);

    await page.getByTestId('logout').click();
    await expect(page).toHaveURL(/\/offline\?next=%2F/);
    await page.goto('/');
    await expect(page.getByTestId('offline-card')).toBeVisible();

    await context.setOffline(false);
    await expect(page).not.toHaveURL(/\/offline/);
  });
});
