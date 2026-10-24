import { BrowserContext, test as base } from '@playwright/test';

export * from '@playwright/test';

/**
 * The Playwright `test` with one automatic fixture: after each test it closes the browser contexts the test opened
 * besides its own `context` (such as those of `signedInAs`), because the browser lives for the whole worker and
 * every open context keeps polling the API until Chromium runs out of connections
 * (`net::ERR_INSUFFICIENT_RESOURCES`).
 */
export const test = base.extend<{ closeOpenedContexts: void }>({
  closeOpenedContexts: [
    async ({ browser, context }, use) => {
      const before = new Set<BrowserContext>(browser.contexts());
      await use();
      await Promise.all(
        browser
          .contexts()
          .filter((opened) => opened !== context && !before.has(opened))
          .map((opened) => opened.close()),
      );
    },
    { auto: true },
  ],
});
