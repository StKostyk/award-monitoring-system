import { Page, expect, test } from '@playwright/test';

import {
  FRESH_PASSWORD,
  SEED,
  freshEmployee,
  registerAndVerify,
  seriousViolations,
  signIn,
  signedIn,
  signedInAs,
} from './helpers';

/** US-008 DoD: the dashboard is usable within this time. */
const USABLE_WITHIN_MS = 3000;

const WIDGETS = ['SUMMARY', 'TIMELINE', 'YEAR_TREND', 'BY_CATEGORY', 'BY_LEVEL', 'RANK'];

async function shownWidgets(page: Page): Promise<string[]> {
  return page
    .locator('[data-testid^="widget-"]')
    .evaluateAll((nodes) => nodes.map((node) => node.getAttribute('data-testid')!.slice(7)));
}

async function noHorizontalScroll(page: Page): Promise<boolean> {
  return page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth);
}

test.describe('personal dashboard', () => {
  test('ac2_4 ac2_5 ac2_11 opens from the navigation with every widget in order within 3 s', async ({
    browser,
  }) => {
    const warmUp = await signedIn(browser, SEED.lecturer);
    await warmUp.goto('/dashboard');
    await expect(warmUp.getByTestId('widget-RANK')).toBeVisible();
    await warmUp.context().close();

    const page = await signedIn(browser, SEED.lecturer);
    const started = Date.now();
    await page.getByTestId('nav-dashboard').click();
    await expect(page.getByTestId('widget-SUMMARY')).toBeVisible();
    await expect(page.getByTestId('widget-RANK')).toBeVisible();
    expect(Date.now() - started).toBeLessThan(USABLE_WITHIN_MS);

    await expect(page).toHaveURL(/\/dashboard$/);
    expect(await shownWidgets(page)).toEqual(WIDGETS);
    await expect(page.getByTestId('tile-approved')).not.toContainText(/^\s*0\s*$/);
    await expect(page.getByTestId('rank-position')).toContainText(/\d+ з \d+ на кафедрі/);
    await expect(page.getByTestId('chart-canvas').first().locator('svg')).toBeVisible();
    expect(await seriousViolations(page)).toEqual([]);
  });

  test('ac2_5 ac2_8 keeps the period in the address and falls back to guidance', async ({
    browser,
  }) => {
    const page = await signedIn(browser, SEED.lecturer);
    await page.goto('/dashboard?from=2024&to=2024');
    await expect(page.getByTestId('widget-YEAR_TREND')).toBeVisible();
    await expect(page.getByTestId('period-from')).toContainText('2024');
    await expect(page.getByTestId('period-to')).toContainText('2024');

    await page.goto('/dashboard?from=1960&to=1961');
    await expect(page.getByTestId('dashboard-empty-period')).toContainText(
      'У цьому періоді немає затверджених нагород',
    );
    await page.getByTestId('empty-all-years').click();
    await expect(page).toHaveURL(/\/dashboard$/);
    await expect(page.getByTestId('widget-SUMMARY')).toBeVisible();
  });

  test('ac2_6 shows a chart as a table whose entries open the award', async ({ browser }) => {
    const page = await signedIn(browser, SEED.lecturer);
    await page.goto('/dashboard');
    const timeline = page.getByTestId('widget-TIMELINE');
    await timeline.getByTestId('chart-table-switch').locator('button').focus();
    await page.keyboard.press('Space');
    const link = timeline.getByTestId('timeline-link').first();
    await expect(link).toHaveAttribute('href', /^\/awards\/\d+$/);
    const href = await link.getAttribute('href');

    await link.focus();
    await page.keyboard.press('Enter');
    await expect(page).toHaveURL(new RegExp(`${href}$`));
  });

  test('ac2_10 fits 360 px in dark mode and English without serious violations', async ({
    browser,
  }) => {
    const page = await signedIn(browser, SEED.lecturer);
    await page.emulateMedia({ colorScheme: 'dark', reducedMotion: 'reduce' });
    await page.setViewportSize({ width: 360, height: 800 });
    await page.goto('/dashboard');
    await expect(page.getByTestId('widget-BY_LEVEL')).toBeVisible();
    await page.getByTestId('language-toggle').click();
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('My dashboard');

    expect(await noHorizontalScroll(page)).toBe(true);
    expect(await seriousViolations(page)).toEqual([]);
  });

  test('ac2_8 ac2_9 guides a new employee and keeps the widget choice across browsers', async ({
    browser,
  }) => {
    const email = await freshEmployee(browser, 'dashboard');
    const page = await signedInAs(browser, email, FRESH_PASSWORD);
    await page.goto('/dashboard');
    await expect(page.getByTestId('dashboard-empty-first').locator('li')).toHaveCount(3);
    await expect(page.getByTestId('empty-new-award')).toHaveAttribute('href', '/awards/new');

    await page.getByTestId('dashboard-customise').click();
    await page.getByTestId('customise-TIMELINE').locator('input').uncheck();
    await page.getByTestId('customise-RANK').locator('input').uncheck();
    await page.getByTestId('customise-save').click();
    await expect(page.getByRole('dialog')).toHaveCount(0);

    const other = await signedInAs(browser, email, FRESH_PASSWORD);
    await other.goto('/dashboard');
    await other.getByTestId('dashboard-customise').click();
    await expect(other.getByTestId('customise-TIMELINE').locator('input')).not.toBeChecked();
    await expect(other.getByTestId('customise-RANK').locator('input')).not.toBeChecked();
    await expect(other.getByTestId('customise-SUMMARY').locator('input')).toBeChecked();
  });

  test('ac2_4 an account without a role has no entry and lands on the forbidden page', async ({
    page,
  }) => {
    const email = `e2e.dashboard.norole.${Date.now()}@chnu.edu.ua`;
    await registerAndVerify(page, email, FRESH_PASSWORD);
    await signIn(page, email, FRESH_PASSWORD);
    await expect(page.getByTestId('user-name')).toBeVisible();
    await expect(page.getByTestId('nav-dashboard')).toHaveCount(0);

    await page.goto('/dashboard');
    await expect(page).toHaveURL(/\/forbidden$/);
  });
});

test.describe('shareable achievement summary', () => {
  test('ac3_1 ac3_2 ac3_4 ac3_5 the summary prints and shares the public achievements', async ({
    browser,
  }) => {
    const page = await signedIn(browser, SEED.lecturer);
    await page.context().grantPermissions(['clipboard-read', 'clipboard-write']);
    await page.goto('/dashboard?from=2020');
    await page.getByTestId('dashboard-summary').click();
    await expect(page).toHaveURL(/\/dashboard\/summary\?from=2020$/);

    await expect(page.getByTestId('summary-name')).not.toBeEmpty();
    await expect(page.getByTestId('summary-department')).not.toBeEmpty();
    await expect(page.getByTestId('summary-period')).toContainText('2020–');
    await expect(page.getByTestId('summary-award').first()).toBeVisible();
    await expect(page.getByTestId('summary-produced')).not.toBeEmpty();
    expect(await seriousViolations(page)).toEqual([]);

    await page.emulateMedia({ media: 'print' });
    await expect(page.locator('.shell__toolbar')).toBeHidden();
    await expect(page.getByTestId('nav-dashboard')).toBeHidden();
    await expect(page.getByTestId('summary-print')).toBeHidden();
    await expect(page.getByTestId('summary-award').first()).toHaveCSS('break-inside', 'avoid');
    await page.emulateMedia({ media: 'screen' });

    await page.getByTestId('summary-copy-link').click();
    await expect(page.getByTestId('summary-copied')).toBeVisible();
    const link = await page.evaluate(() => navigator.clipboard.readText());
    expect(link).toMatch(/^http:\/\/[^/]+\/public\/achievements\?person=\d+$/);
    const name = (await page.getByTestId('summary-name').textContent())!.trim();

    const visitor = await (await browser.newContext()).newPage();
    await visitor.goto(new URL(link).pathname + new URL(link).search);
    await expect(visitor.getByTestId('achievements-title')).toContainText(name);
    await expect(visitor.getByTestId('achievement-card').first()).toBeVisible();
    const recipients = await visitor.getByTestId('achievement-recipient').allTextContents();
    expect(recipients.every((recipient) => recipient.includes(name))).toBe(true);
    expect(await seriousViolations(visitor)).toEqual([]);
  });

  test('ac3_1 ac3_4 a new employee gets the hint and a period that is not valid is refused', async ({
    browser,
  }) => {
    const email = await freshEmployee(browser, 'summary');
    const page = await signedInAs(browser, email, FRESH_PASSWORD);
    await page.goto('/dashboard/summary');
    await expect(page.getByTestId('summary-empty')).toBeVisible();
    await expect(page.getByTestId('summary-public-hint')).toBeVisible();
    await expect(page.getByTestId('summary-copy-link')).toHaveCount(0);

    await page.goto('/dashboard/summary?from=2026&to=2020');
    await expect(page.getByTestId('summary-invalid')).toBeVisible();
    await page.getByTestId('summary-back').click();
    await expect(page).toHaveURL(/\/dashboard$/);
  });
});
