import { Browser, Page, expect, test } from './fixtures';
import {
  FRESH_PASSWORD,
  freshEmployee,
  pastDay,
  seriousViolations,
  signedInAs,
  sql,
  submitWithoutDocuments,
} from './helpers';

/** The default order of a dashboard with one approved award: no facts, KPI alone in the side column. */
const DEFAULT_ORDER = [
  'KPI',
  'TREND',
  'LEVEL_STAIRS',
  'IN_PROGRESS',
  'LATEST',
  'BY_CATEGORY',
  'RANK',
  'VISIBILITY',
];
const WITHOUT_LATEST = DEFAULT_ORDER.filter((widget) => widget !== 'LATEST');
const LATEST_UP = [
  'KPI',
  'TREND',
  'LEVEL_STAIRS',
  'LATEST',
  'IN_PROGRESS',
  'BY_CATEGORY',
  'RANK',
  'VISIBILITY',
];

/** A fresh employee with one approved award, so that the dashboard shows its widgets. */
async function employeeWithAward(browser: Browser): Promise<string> {
  const email = await freshEmployee(browser, 'layout');
  const page = await signedInAs(browser, email, FRESH_PASSWORD);
  await page.getByTestId('nav-awards').click();
  await page.getByTestId('award-add').click();
  await page.getByTestId('upload-skip').click();
  await page.getByTestId('award-title-uk').fill('Подяка за розташування віджетів');
  await page.getByTestId('award-save').click();
  await expect(page).toHaveURL(/\/awards\/\d+\/edit$/);
  const id = /\/awards\/(\d+)\/edit/.exec(page.url())?.[1] ?? '';
  await page.getByTestId('award-category').click();
  await page.getByTestId('category-option-13').click();
  await page.getByTestId('award-organization').fill('Міністерство освіти і науки України');
  await page.getByTestId('award-date').fill(pastDay());
  await submitWithoutDocuments(page);
  await expect(page.getByTestId('award-submitted-name')).toBeVisible();
  await page.context().close();
  sql(
    `update awards set status = 'APPROVED' where award_id = ${id}; ` +
      `update award_requests set status = 'APPROVED', completed_at = now() where award_id = ${id}`,
  );
  return email;
}

function saved(page: Page): Promise<unknown> {
  return page.waitForResponse(
    (response) =>
      response.url().endsWith('/users/me/dashboard-preferences') &&
      response.request().method() === 'PUT' &&
      response.ok(),
  );
}

async function shown(page: Page): Promise<string[]> {
  return page
    .locator('app-dashboard-layout [data-testid^="widget-"]:not([data-testid="widget-TIMELINE"])')
    .evaluateAll((nodes) => nodes.map((node) => node.getAttribute('data-testid')!.slice(7)));
}

async function listed(page: Page): Promise<string[]> {
  return page
    .locator('[data-testid^="layout-item-"]')
    .evaluateAll((nodes) => nodes.map((node) => node.getAttribute('data-testid')!.slice(12)));
}

test.describe('scheme and dashboard layout on the account', () => {
  test('ac1_1 ac1_2 a scheme picked in one browser opens in another after sign-in', async ({
    browser,
  }) => {
    const email = await freshEmployee(browser, 'scheme');
    const first = await signedInAs(browser, email, FRESH_PASSWORD);
    await first.getByTestId('color-scheme-menu').click();
    const stored = saved(first);
    await first.getByTestId('color-scheme-dark').click();
    await stored;

    const context = await browser.newContext({ colorScheme: 'light' });
    const second = await context.newPage();
    await second.goto('/');
    await expect(second.locator('html')).not.toHaveAttribute('data-color-scheme', /.+/);
    await context.close();

    const other = await signedInAs(browser, email, FRESH_PASSWORD);
    await expect(other.locator('html')).toHaveAttribute('data-color-scheme', 'dark');
    await other.reload();
    await expect(other.locator('html')).toHaveAttribute('data-color-scheme', 'dark');
  });

  test('ac1_3 ac1_8 ac2_3 moves and hides widgets by keyboard within their column, keeps them and resets them', async ({
    browser,
  }) => {
    const email = await employeeWithAward(browser);
    const page = await signedInAs(browser, email, FRESH_PASSWORD);
    await page.goto('/dashboard');
    await expect(page.getByTestId('widget-RANK')).toBeVisible();
    await expect.poll(() => shown(page)).toEqual(DEFAULT_ORDER);
    await expect(page.getByTestId('layout-side').getByTestId('widget-KPI')).toBeVisible();
    await expect(page.getByTestId('widget-FACT')).toHaveCount(0);

    await page.getByTestId('dashboard-customise').click();
    await expect(page.getByTestId('layout-down-KPI')).toBeDisabled();
    await expect(page.getByTestId('layout-up-TREND')).toBeDisabled();
    await page.getByTestId('layout-up-LATEST').focus();
    await page.keyboard.press('Enter');
    await expect(page.getByTestId('layout-up-LATEST')).toBeFocused();
    await expect(page.getByTestId('layout-announcement')).toHaveText(
      '«Останні нагороди»: позиція 3 з 7',
    );
    await page.keyboard.press('Tab');
    await page.keyboard.press('Tab');
    await page.keyboard.press('Space');
    await expect(page.getByTestId('layout-hide-LATEST')).toHaveAttribute('aria-pressed', 'true');
    expect(await seriousViolations(page)).toEqual([]);

    const stored = saved(page);
    await page.getByTestId('layout-done').click();
    await stored;
    await expect.poll(() => shown(page)).toEqual(WITHOUT_LATEST);

    await page.reload();
    await expect(page.getByTestId('widget-RANK')).toBeVisible();
    await expect.poll(() => shown(page)).toEqual(WITHOUT_LATEST);

    await page.getByTestId('dashboard-customise').click();
    await expect.poll(() => listed(page)).toEqual(LATEST_UP);
    await page.getByTestId('layout-reset').click();
    const reset = saved(page);
    await page.getByTestId('layout-done').click();
    await reset;
    await expect.poll(() => shown(page)).toEqual(DEFAULT_ORDER);
  });
});
