import { defineConfig, devices } from '@playwright/test';

/** Set for the specs that need the production build (Compose at `http://localhost`); unset, the dev server runs. */
const baseURL = process.env['PLAYWRIGHT_BASE_URL'];

export default defineConfig({
  testDir: './e2e',
  testIgnore: baseURL ? undefined : ['**/pwa.spec.ts'],
  globalSetup: './e2e/global-setup.ts',
  timeout: 60_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  retries: process.env['CI'] ? 1 : 0,
  reporter: process.env['CI'] ? [['github'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: baseURL ?? 'http://localhost:4200',
    locale: 'uk-UA',
    trace: 'retain-on-failure',
  },
  webServer: baseURL
    ? undefined
    : {
        command: 'npm start',
        url: 'http://localhost:4200',
        reuseExistingServer: !process.env['CI'],
        timeout: 180_000,
      },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
