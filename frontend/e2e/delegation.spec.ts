import { expect, test } from './fixtures';

import { DEMO_PASSWORD, SEED, signIn } from './helpers';

const { employee, secretary, dean } = SEED;
function inDays(count: number): string {
  const date = new Date();
  date.setDate(date.getDate() + count);
  return date.toISOString().substring(0, 10);
}

type Page = import('@playwright/test').Page;

/** Opens the page and waits for the list itself, because the empty row shows while the request is in flight. */
async function listDelegations(page: Page): Promise<void> {
  const loaded = page.waitForResponse(
    (response) =>
      response.request().method() === 'GET' && /\/api\/v1\/delegations(\?|$)/.test(response.url()),
  );
  if (new URL(page.url()).pathname === '/delegations') {
    await page.reload();
  } else {
    await page.goto('/delegations');
  }
  await loaded;
}

function activeGiven(page: Page) {
  return page
    .getByTestId('column-active')
    .getByTestId('given-card')
    .filter({ hasText: 'Секретар' })
    .first();
}

/** Revokes the delegation of the card from its own button. */
async function revokeFromCard(page: Page, card: ReturnType<typeof activeGiven>): Promise<void> {
  await card.getByTestId('revoke-delegation').click();
}

test.describe('approval delegation', () => {
  test.describe.configure({ mode: 'serial' });

  test.beforeAll(async ({ browser }) => {
    const page = await browser.newPage();
    await signIn(page, dean, DEMO_PASSWORD);
    const standing = activeGiven(page);
    for (let left = 10; left > 0; left -= 1) {
      await listDelegations(page);
      if (!(await standing.count())) {
        break;
      }
      await revokeFromCard(page, standing);
      await page.getByTestId('revoke-delegation-confirm').click();
      await expect(page.getByTestId('delegations-message')).toBeVisible();
    }
    await page.close();
  });

  test('ac31 ac35 the dean delegates the approval authority to the secretary', async ({ page }) => {
    await signIn(page, dean, DEMO_PASSWORD);
    await page.getByTestId('nav-delegations').click();

    await expect(page).toHaveURL(/\/delegations$/);

    await page.getByTestId('delegate-open').click();
    await page.getByTestId('delegate-role').click();
    await page.getByRole('option', { name: 'Декан' }).click();
    await page.getByTestId('delegate-search').fill('secretary.fmi');
    await page.getByRole('option', { name: /secretary\.fmi/ }).click();
    await page.getByTestId('delegate-valid-to').fill(inDays(14));
    await page.getByTestId('delegate-reason').fill('Відпустка');
    await page.getByTestId('delegate-submit').click();

    await expect(page.getByTestId('delegations-message')).toContainText('Повноваження делеговано');

    await expect(activeGiven(page).getByTestId('card-note')).toContainText('Відпустка');
    await expect(activeGiven(page).getByTestId('revoke-delegation')).toBeVisible();
  });

  test('ac32 the secretary acts for the dean and cannot pass the role on', async ({ page }) => {
    await signIn(page, secretary, DEMO_PASSWORD);

    await expect(page.getByTestId('acting-for')).toContainText('Діє за дорученням');

    await page.getByTestId('nav-delegations').click();

    await page.getByTestId('side-received').click();

    await expect(page.getByTestId('side-received')).toHaveClass(/checked/);
    const card = page
      .getByTestId('column-active')
      .getByTestId('received-card')
      .filter({ hasText: 'Мартинюк' })
      .first();

    await expect(card.getByTestId('card-role')).toContainText('Декан');
    await expect(card.getByTestId('card-days-left')).toContainText(/\d+/);

    await card.getByTestId('card-limits').locator('summary').click();

    await expect(card.getByTestId('card-can')).toContainText(
      'Розглядати подання на рівні факультету',
    );
    await expect(card.getByTestId('card-cannot')).toContainText('Передавати ці повноваження далі');
    await expect(page.getByTestId('revoke-delegation')).toHaveCount(0);

    await page.getByTestId('delegate-open').click();
    await page.getByTestId('delegate-role').click();

    await expect(page.getByRole('option', { name: 'Декан', exact: true })).toHaveCount(0);
    await expect(page.getByRole('option', { name: 'Секретар факультету' })).toBeVisible();
  });

  test('ac34 the dean revokes the delegation', async ({ page }) => {
    await signIn(page, dean, DEMO_PASSWORD);
    await listDelegations(page);
    const row = activeGiven(page);

    await revokeFromCard(page, row);

    await expect(page.getByTestId('revoke-delegation-text')).toContainText('Декан');
    await page.getByTestId('revoke-delegation-cancel').click();

    await expect(row).toBeVisible();

    await row.getByTestId('revoke-delegation').click();
    await page.getByTestId('revoke-delegation-confirm').click();

    await expect(page.getByTestId('delegations-message')).toContainText('Делегування відкликано');
    await expect(activeGiven(page)).toHaveCount(0);
    const ended = page
      .getByTestId('column-ended')
      .getByTestId('given-card')
      .filter({ hasText: 'Секретар' })
      .filter({ hasText: 'Відкликано' })
      .first();

    await expect(ended).toBeVisible();
    await expect(ended.getByTestId('revoke-delegation')).toHaveCount(0);
  });

  test('ac6_3 the board stacks active, scheduled and ended on a phone', async ({ page }) => {
    await page.setViewportSize({ width: 360, height: 800 });
    await signIn(page, dean, DEMO_PASSWORD);
    await listDelegations(page);
    const tops: number[] = [];
    for (const key of ['active', 'upcoming', 'ended']) {
      const box = await page.getByTestId(`column-${key}`).boundingBox();
      tops.push(box?.y ?? -1);
    }

    expect(tops[0]).toBeGreaterThanOrEqual(0);
    expect(tops[1]).toBeGreaterThan(tops[0]);
    expect(tops[2]).toBeGreaterThan(tops[1]);
    expect(
      await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
    ).toBe(true);
  });

  test('ac36 an employee has no entry and lands on the forbidden page', async ({ page }) => {
    await signIn(page, employee, DEMO_PASSWORD);

    await expect(page.getByTestId('nav-delegations')).toHaveCount(0);

    await page.goto('/delegations');

    await expect(page).toHaveURL(/\/forbidden$/);
    await expect(page.getByTestId('forbidden-card')).toContainText('Доступ заборонено');
  });
});
