import { FullConfig, chromium, expect } from '@playwright/test';

import { DEMO_PASSWORD, NOTICE_VERSION, SEED, expireOpenRequests, sql } from './helpers';

const WARM_UP_TIMEOUT = 120_000;

/**
 * Records the acknowledgement of the current privacy notice for every account whose latest acknowledgement is of
 * another version (or missing), so that seed accounts open the application instead of the acknowledgement page,
 * also after a manual run with another notice version; the redirect itself is covered by
 * `privacy-notice.spec.ts` with an account of its own. Closes the review requests that earlier runs left open
 * (awards titled with a `uniqueToken` suffix or owned by an `e2e.` account), so that their overdue deadlines do
 * not fill the first page of the review queues ahead of the requests a spec submits. Signs a seed account in once,
 * so that the first spec does not pay for the first compilation of the lazy routes and the warm-up of the backend.
 */
export default async function globalSetup(config: FullConfig): Promise<void> {
  expireOpenRequests(`a.title_uk ~ ' [a-z]{12}$' or u.email_address like 'e2e.%'`);
  sql(
    `insert into consent_records (user_id, consent_type, consent_version, is_granted, granted_at)
     select u.user_id, 'DATA_PROCESSING', '${NOTICE_VERSION}', true, now() from users u
     where coalesce((select c.consent_version from consent_records c where c.user_id = u.user_id
       and c.consent_type = 'DATA_PROCESSING' order by c.created_at desc, c.consent_id desc
       limit 1), '') <> '${NOTICE_VERSION}'`,
  );
  const browser = await chromium.launch();
  try {
    const page = await browser.newPage({
      baseURL: config.projects[0].use.baseURL,
      locale: 'uk-UA',
    });
    await page.goto('/');
    await expect(page).toHaveURL(/\/login$/, { timeout: WARM_UP_TIMEOUT });
    await page.fill('#username', SEED.employee);
    await page.fill('#password', DEMO_PASSWORD);
    await page.click('button[type="submit"]');
    await expect(page.getByTestId('nav-awards')).toBeVisible({ timeout: WARM_UP_TIMEOUT });
    await page.getByTestId('nav-awards').click();
    await expect(page.getByTestId('award-add')).toBeVisible({ timeout: WARM_UP_TIMEOUT });
  } finally {
    await browser.close();
  }
}
