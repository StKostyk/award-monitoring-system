import { Browser, Page, expect, test } from './fixtures';
import {
  FRESH_PASSWORD,
  SEED,
  freshEmployee,
  seriousViolations,
  signInAsSeed,
  signedIn,
  signedInAs,
  sql,
} from './helpers';

const DEFAULT_ORDER = ['KPI', 'TREND', 'RACE', 'RECORDS', 'FACT', 'TOP', 'LEVEL_MAP'];
const FMI = 9;
const PHILOLOGY = 12;

function userId(email: string): string {
  return sql(`select user_id from users where email_address = '${email}'`);
}

/** A fresh account with its own DEAN role over the faculty of philology. */
async function freshDean(browser: Browser, name: string): Promise<string> {
  const email = await freshEmployee(browser, name);
  sql(
    `insert into user_roles (user_id, organization_id, role_type) values (${userId(email)}, ${PHILOLOGY}, 'DEAN')`,
  );
  return email;
}

async function shown(page: Page): Promise<string[]> {
  return page
    .locator('app-dashboard-layout [data-testid^="widget-"]')
    .evaluateAll((nodes) => nodes.map((node) => node.getAttribute('data-testid')!.slice(7)));
}

function saved(page: Page): Promise<unknown> {
  return page.waitForResponse(
    (response) =>
      response.url().endsWith('/users/me/dashboard-preferences') &&
      response.request().method() === 'PUT' &&
      response.ok(),
  );
}

test.describe('Hall of Fame', () => {
  test('ac3_1 ac3_3 a rector opens / and lands on the hall of fame of the university', async ({
    browser,
  }) => {
    sql(
      `update dashboard_preferences set layouts = layouts - 'HALL_OF_FAME' where user_id = ${userId(SEED.rector)}`,
    );
    const page = await signedIn(browser, SEED.rector);

    await page.goto('/');
    await expect(page).toHaveURL(/\/hall-of-fame$/);
    await expect(page.getByRole('heading', { name: 'Зала визнання', level: 1 })).toBeVisible();
    await expect(page.getByTestId('hall-unit')).toContainText('Чернівецький національний');
    await expect(page.getByTestId('widget-LEVEL_MAP')).toBeVisible();
    await expect.poll(() => shown(page)).toEqual(DEFAULT_ORDER);
    await expect(page.getByTestId('hall-analytics')).toHaveAttribute(
      'href',
      '/analytics/university',
    );
    await expect(page.getByTestId('board-report')).toBeVisible();
    await expect(page.getByTestId('nav-hall-of-fame')).toBeVisible();
    await expect(page.getByTestId('nav-university-analytics')).toBeVisible();
    expect(await seriousViolations(page)).toEqual([]);
  });

  test('ac3_2 a dean sees the hall of fame of its faculty without a picker', async ({
    browser,
  }) => {
    const page = await signedIn(browser, SEED.dean);

    await page.goto('/');
    await expect(page).toHaveURL(/\/hall-of-fame$/);
    await expect(page.getByTestId('hall-unit')).toHaveText('Факультет математики та інформатики');
    await expect(page.getByTestId('widget-TOP')).toBeVisible();
    await expect(page.getByTestId('unit-picker')).toHaveCount(0);
    await expect(page.getByTestId('board-report')).toHaveCount(0);
    await expect(page.getByTestId('hall-analytics')).toHaveAttribute(
      'href',
      `/analytics/units/${FMI}`,
    );
  });

  test('ac3_2 a dean of two faculties switches faculty and keeps it in the address', async ({
    browser,
  }) => {
    const email = await freshDean(browser, 'hall-two');
    sql(
      `insert into user_roles (user_id, organization_id, role_type) values (${userId(email)}, ${FMI}, 'DEAN')`,
    );
    const page = await signedInAs(browser, email, FRESH_PASSWORD);

    await page.goto('/hall-of-fame');
    await expect(page.getByTestId('unit-picker')).toBeVisible();
    await expect(page.getByTestId('hall-unit')).toHaveText('Факультет математики та інформатики');
    await page.getByTestId('unit-picker').click();
    await page.getByTestId(`unit-option-${PHILOLOGY}`).click();
    await expect(page).toHaveURL(new RegExp(`/hall-of-fame\\?unit=${PHILOLOGY}$`));
    await expect(page.getByTestId('hall-unit')).toHaveText('Філологічний факультет');

    await page.reload();
    await expect(page.getByTestId('hall-unit')).toHaveText('Філологічний факультет');
    await page.getByTestId('unit-picker').click();
    await page.getByTestId(`unit-option-${FMI}`).click();
    await expect(page).toHaveURL(new RegExp(`/hall-of-fame\\?unit=${FMI}$`));
    await expect(page.getByTestId('hall-unit')).toHaveText('Факультет математики та інформатики');
  });

  test('ac3_4 a borrowed dean role opens no hall of fame, as delegations carry no analytics', async ({
    browser,
  }) => {
    const delegator = await freshDean(browser, 'hall-lender');
    sql(
      `insert into user_roles (user_id, organization_id, role_type) values (${userId(delegator)}, ${FMI}, 'DEAN')`,
    );
    const delegate = await freshEmployee(browser, 'hall-delegate');
    sql(
      `insert into role_delegations (delegator_id, delegate_id, organization_id, role_type, valid_from, valid_to) ` +
        `values (${userId(delegator)}, ${userId(delegate)}, ${FMI}, 'DEAN', current_date, current_date + 7)`,
    );
    const page = await signedInAs(browser, delegate, FRESH_PASSWORD);

    await page.goto('/');
    await expect(page).toHaveURL(/\/dashboard$/);
    await expect(page.getByTestId('nav-hall-of-fame')).toHaveCount(0);
    await page.goto('/hall-of-fame');
    await expect(page).toHaveURL(/\/forbidden$/);
  });

  test('ac3_3 ac3_4 an employee is sent to its dashboard and is refused the hall of fame', async ({
    browser,
  }) => {
    const email = await freshEmployee(browser, 'hall-employee');
    const page = await signedInAs(browser, email, FRESH_PASSWORD);

    await page.goto('/');
    await expect(page).toHaveURL(/\/$/);
    await expect(page.getByTestId('nav-hall-of-fame')).toHaveCount(0);

    await page.goto('/hall-of-fame');
    await expect(page).toHaveURL(/\/forbidden$/);
  });

  test('9.5 ac1_6 the trend annotations stay inside the chart at 360 px in English', async ({
    page,
  }) => {
    await page.setViewportSize({ width: 360, height: 780 });
    await signInAsSeed(page, SEED.dean);
    await page.evaluate(() => localStorage.setItem('lang', 'en'));
    await page.goto('/hall-of-fame');
    const chart = page.getByTestId('widget-TREND').locator('[data-testid="chart-canvas"] svg');
    await expect(chart.locator('text', { hasText: /Peak|Year to date/ }).first()).toBeVisible();

    const outside = await chart.evaluate((svg) => {
      const frame = svg.getBoundingClientRect();
      return Array.from(svg.querySelectorAll('text'))
        .filter((text) => /Peak|Year to date/.test(text.textContent ?? ''))
        .map((text) => ({ text: text.textContent, box: text.getBoundingClientRect() }))
        .filter(
          ({ box }) =>
            box.left < frame.left - 1 ||
            box.right > frame.right + 1 ||
            box.top < frame.top - 1 ||
            box.bottom > frame.bottom + 1,
        )
        .map(({ text }) => text);
    });
    expect(outside).toEqual([]);
  });

  test('ac3_5 the widgets follow the hall of fame layout and stack at 360 px', async ({
    browser,
  }) => {
    const email = await freshDean(browser, 'hall-layout');
    const page = await signedInAs(browser, email, FRESH_PASSWORD);

    await page.goto('/hall-of-fame');
    await expect(page.getByTestId('widget-LEVEL_MAP')).toBeVisible();
    await expect.poll(() => shown(page)).toEqual(DEFAULT_ORDER);

    await page.getByTestId('dashboard-customise').click();
    await page.getByTestId('layout-up-RACE').click();
    await page.getByTestId('layout-up-RACE').click();
    await page.getByTestId('layout-hide-LEVEL_MAP').click();
    const stored = saved(page);
    await page.getByTestId('layout-done').click();
    await stored;

    await page.reload();
    await expect(page.getByTestId('widget-RACE')).toBeVisible();
    await expect
      .poll(() => shown(page))
      .toEqual(['RACE', 'KPI', 'TREND', 'RECORDS', 'FACT', 'TOP']);

    await page.setViewportSize({ width: 360, height: 780 });
    await expect(page.getByTestId('widget-TOP')).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(
      360,
    );
    const lefts = await page
      .locator('app-dashboard-layout [data-testid^="widget-"]')
      .evaluateAll((nodes) => nodes.map((node) => Math.round(node.getBoundingClientRect().left)));
    expect(new Set(lefts).size).toBe(1);
    expect(await seriousViolations(page)).toEqual([]);
  });
});
