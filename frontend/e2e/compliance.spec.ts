import { expect, test } from './fixtures';

import {
  FRESH_PASSWORD,
  SEED,
  freshEmployee,
  seriousViolations,
  signIn,
  signInAsSeed,
  sql,
} from './helpers';

const { gdpr } = SEED;
const POLICY_COUNT = 9;
/** Ids far above the job's own sequence, so the fixture runs never collide with a real run. */
const RUN_ID = 900_001;

test.describe('Compliance page', () => {
  test('ac25 ac71 ac72 the officer opens the three sections and the url keeps section and filter', async ({
    page,
  }) => {
    await signInAsSeed(page, gdpr);
    await page.getByTestId('nav-compliance').click();

    await expect(page).toHaveURL(/\/compliance$/);
    await expect(page.getByTestId('policies-table').locator('tbody tr')).toHaveCount(POLICY_COUNT);
    await expect(page.getByTestId('retention-loading')).toHaveCount(0);
    await expect(page.getByTestId('count-retention')).toHaveText(String(POLICY_COUNT));
    await expect(page.getByTestId('count-erasures')).toHaveText(/^\d+$/);
    await expect(page.getByTestId('count-holds')).toHaveText(/^\d+$/);
    if ((await page.getByTestId('runs-empty').count()) === 0) {
      await expect(page.getByTestId('run-durations')).toBeVisible();
    }
    expect(await seriousViolations(page)).toEqual([]);

    await page.getByTestId('section-erasures').click();

    await expect(page).toHaveURL(/tab=erasures/);
    await expect(page.getByTestId('erasures-table')).toBeVisible();
    await page.getByTestId('erasure-filter-COMPLETED').click();

    await expect(page).toHaveURL(/status=COMPLETED/);
    await expect(page.getByTestId('erasures-loading')).toHaveCount(0);
    expect(await seriousViolations(page)).toEqual([]);

    await page.getByTestId('section-holds').click();
    await page.getByTestId('hold-filter-all').click();

    await expect(page).toHaveURL(/tab=holds/);
    await expect(page).toHaveURL(/holds=all/);
    await expect(page.getByTestId('holds-loading')).toHaveCount(0);
    expect(await seriousViolations(page)).toEqual([]);

    await page.reload();

    await expect(page.getByTestId('section-holds')).toHaveAttribute('aria-current', 'true');
    await expect(page.getByTestId('holds-tab')).toBeVisible();
    await expect(page.getByTestId('hold-filter-all')).toHaveClass(/checked/);

    await page.goBack();

    await expect(page).not.toHaveURL(/holds=all/);
    await expect(page.getByTestId('hold-filter-active')).toHaveClass(/checked/);

    await page.goBack();

    await expect(page).toHaveURL(/tab=erasures/);
    await expect(page.getByTestId('erasure-filter-COMPLETED')).toHaveClass(/checked/);
  });

  test('ac72 the retention section charts the run durations with a failed run marked', async ({
    page,
  }) => {
    const ids = [RUN_ID, RUN_ID + 1];
    sql(
      `insert into retention_runs(run_id, started_at, finished_at, status, results) values
       (${ids[0]}, now() - interval '9 day', now() - interval '9 day' + interval '42 second', 'COMPLETED', '[]'),
       (${ids[1]}, now() - interval '8 day', now() - interval '8 day' + interval '75 second', 'PARTIAL', '[]')
       on conflict (run_id) do nothing`,
    );
    try {
      await signInAsSeed(page, gdpr);
      await page.goto('/compliance');

      await expect(page.getByTestId('run-durations')).toBeVisible();
      await page.getByTestId('chart-table-switch').locator('button').click();

      const rows = page.getByTestId('run-duration-row');
      await expect(rows.filter({ hasText: '75' })).toHaveAttribute('data-failed', 'true');
      await expect(rows.filter({ hasText: '42' })).toHaveAttribute('data-failed', 'false');
      expect(await seriousViolations(page)).toEqual([]);
    } finally {
      sql(`delete from retention_runs where run_id in (${ids.join(', ')})`);
    }
  });

  test('ac25 an employee has no entry and lands on the forbidden page', async ({
    browser,
    page,
  }) => {
    const employee = await freshEmployee(browser, 'compliance');
    await signIn(page, employee, FRESH_PASSWORD);

    await expect(page.getByTestId('nav-awards')).toBeVisible();
    await expect(page.getByTestId('nav-compliance')).toHaveCount(0);

    await page.goto('/compliance');

    await expect(page).toHaveURL(/\/forbidden$/);
  });
});
