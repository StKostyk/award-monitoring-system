import { expect, test } from '@playwright/test';

import { signIn } from './helpers';

const demo = 'Passw0rd-demo';
const dean = 'dean.fmi@chnu.edu.ua';
const secretary = 'secretary.fmi@chnu.edu.ua';
const employee = 'employee.fmi@chnu.edu.ua';

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
    .getByTestId('given-table')
    .getByRole('row')
    .filter({ hasText: 'Секретар' })
    .filter({ hasText: 'Активне' })
    .first();
}

test.describe('approval delegation', () => {
  test.describe.configure({ mode: 'serial' });

  test.beforeAll(async ({ browser }) => {
    const page = await browser.newPage();
    await signIn(page, dean, demo);
    const standing = activeGiven(page);
    for (let left = 10; left > 0; left -= 1) {
      await listDelegations(page);
      if (!(await standing.count())) {
        break;
      }
      await standing.getByTestId('revoke-delegation').click();
      await page.getByTestId('revoke-delegation-confirm').click();
      await expect(page.getByTestId('delegations-message')).toBeVisible();
    }
    await page.close();
  });

  test('ac31 ac35 the dean delegates the approval authority to the secretary', async ({ page }) => {
    await signIn(page, dean, demo);
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

    await expect(activeGiven(page).getByTestId('given-state')).toContainText('Активне');
  });

  test('ac32 the secretary acts for the dean and cannot pass the role on', async ({ page }) => {
    await signIn(page, secretary, demo);

    await expect(page.getByTestId('acting-for')).toContainText('Діє за дорученням');

    await page.getByTestId('nav-delegations').click();
    const row = page
      .getByTestId('received-table')
      .getByRole('row')
      .filter({ hasText: 'Декан' })
      .filter({ hasText: 'Активне' })
      .first();

    await expect(row.getByTestId('received-state')).toContainText('Активне');

    await page.getByTestId('delegate-open').click();
    await page.getByTestId('delegate-role').click();

    await expect(page.getByRole('option', { name: 'Декан', exact: true })).toHaveCount(0);
    await expect(page.getByRole('option', { name: 'Секретар факультету' })).toBeVisible();
  });

  test('ac34 the dean revokes the delegation', async ({ page }) => {
    await signIn(page, dean, demo);
    await listDelegations(page);
    const row = activeGiven(page);

    await row.getByTestId('revoke-delegation').click();

    await expect(page.getByTestId('revoke-delegation-text')).toContainText('Декан');
    await page.getByTestId('revoke-delegation-cancel').click();

    await expect(row.getByTestId('given-state')).toContainText('Активне');

    await row.getByTestId('revoke-delegation').click();
    await page.getByTestId('revoke-delegation-confirm').click();

    await expect(page.getByTestId('delegations-message')).toContainText('Делегування відкликано');
    await expect(activeGiven(page)).toHaveCount(0);
    await expect(
      page
        .getByTestId('given-table')
        .getByRole('row')
        .filter({ hasText: 'Секретар' })
        .filter({ hasText: 'Відкликано' })
        .first(),
    ).toBeVisible();
  });

  test('ac36 an employee has no entry and lands on the forbidden page', async ({ page }) => {
    await signIn(page, employee, demo);

    await expect(page.getByTestId('nav-delegations')).toHaveCount(0);

    await page.goto('/delegations');

    await expect(page).toHaveURL(/\/forbidden$/);
    await expect(page.getByTestId('forbidden-card')).toContainText('Доступ заборонено');
  });
});
