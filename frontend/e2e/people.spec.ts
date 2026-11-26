import { Page } from '@playwright/test';

import { expect, test } from './fixtures';

import {
  DEMO_PASSWORD,
  SEED,
  countMessages,
  openNewAward,
  seriousViolations,
  signIn,
  uniqueToken,
  userRow,
  userTableRow,
} from './helpers';

const { secretary, rectorSecretary } = SEED;
const DEPARTMENT = 'Кафедра алгебри';
const INVITATION = 'Вас додано до реєстру нагород ЧНУ';

function surname(): string {
  const token = uniqueToken();
  return `Test${token.charAt(0).toUpperCase()}${token.substring(1)}`;
}

async function addPerson(page: Page, lastName: string, email: string): Promise<void> {
  await page.getByTestId('person-add-open').click();
  await page.getByTestId('person-last-name').fill(lastName);
  await page.getByTestId('person-first-name').fill('Petro');
  await page.getByTestId('person-department').click();
  await page.getByRole('option', { name: DEPARTMENT }).click();
  await page.getByTestId('person-email').fill(email);
  await page.getByTestId('person-save').click();
}

async function removeOpenPerson(page: Page): Promise<void> {
  await page.getByTestId('person-remove').click();
  await page.getByTestId('confirm-accept').click();
  await expect(page).toHaveURL(/\/admin\/users$/);
}

test.describe('people without an account', () => {
  test('ac1_1 ac1_4 ac1_5 ac1_9 the secretary adds a colleague who gets one invitation', async ({
    page,
  }) => {
    const lastName = surname();
    const email = `${lastName.toLowerCase()}@chnu.edu.ua`;
    await signIn(page, secretary, DEMO_PASSWORD);
    await page.goto('/admin/users');

    await addPerson(page, lastName, email);

    await expect(page).toHaveURL(/\/admin\/users\/\d+$/);
    await expect(page.getByTestId('detail-email')).toContainText(email);
    await expect(page.getByTestId('person-invitation')).toContainText('Надіслано');
    await expect(page.getByTestId('person-no-awards')).toBeVisible();
    await expect(page.getByTestId('assign-role-open')).toHaveCount(0);
    expect(await seriousViolations(page)).toEqual([]);
    await expect.poll(() => countMessages(email, INVITATION), { timeout: 20_000 }).toBe(1);

    await page.getByTestId('person-invite').click();

    await expect(page.getByTestId('person-error')).toContainText(
      'Запрошення вже надіслано, спробуйте пізніше',
    );

    await page.goto('/admin/users');
    await page.getByTestId('filter-q').fill(lastName);
    await expect(userTableRow(page, lastName).getByTestId('unclaimed-badge')).toHaveText(
      'Без облікового запису',
    );

    await userRow(page, lastName).click();
    await removeOpenPerson(page);
    await page.getByTestId('filter-q').fill(lastName);

    await expect(page.getByTestId('users-empty')).toBeVisible();
  });

  test('ac1_4 ac1_10 a namesake without an address is added after the duplicate warning', async ({
    page,
  }) => {
    const lastName = surname();
    await signIn(page, secretary, DEMO_PASSWORD);
    await page.goto('/admin/users');
    await addPerson(page, lastName, '');

    await expect(page.getByTestId('person-invitation')).toContainText(
      'Запрошення не надіслано: адреса невідома',
    );
    await expect(page.getByTestId('detail-email')).toContainText('Адреса невідома');
    await expect(page.getByTestId('person-invite')).toHaveCount(0);
    const first = page.url();

    await page.goto('/admin/users');
    await addPerson(page, lastName, '');

    await expect(page.getByTestId('person-duplicates')).toContainText(`${lastName} Petro`);
    await expect(page.getByTestId('person-duplicates')).toContainText('нагород: 0');
    expect(await seriousViolations(page)).toEqual([]);

    await page.getByTestId('person-add-anyway').click();

    await expect(page).toHaveURL(/\/admin\/users\/\d+$/);
    expect(page.url()).not.toBe(first);

    await removeOpenPerson(page);
    await page.goto(first);
    await removeOpenPerson(page);
  });

  test('ac2_1 ac2_7 the secretary enters a draft for a colleague without an account', async ({
    page,
  }) => {
    const lastName = surname();
    const title = `Грамота колеги ${uniqueToken()}`;
    await signIn(page, secretary, DEMO_PASSWORD);
    await page.goto('/admin/users');
    await addPerson(page, lastName, '');
    await expect(page).toHaveURL(/\/admin\/users\/\d+$/);
    const personPage = page.url();

    await page.getByTestId('person-add-award').click();

    await expect(page).toHaveURL(/\/awards\/new\?person=\d+$/);
    await expect(page.getByTestId('upload-step')).toHaveCount(0);
    await expect(page.getByTestId('award-recipient-colleague')).toHaveClass(
      /mat-mdc-radio-checked/,
    );
    await expect(page.getByTestId('award-recipient-colleague-search')).toHaveValue(
      `${lastName} Petro`,
    );
    expect(await seriousViolations(page)).toEqual([]);

    await page.getByTestId('award-title-uk').fill(title);
    await page.getByTestId('award-save').click();

    await expect(page).toHaveURL(/\/awards\/\d+\/edit$/);
    await expect(page.getByTestId('award-form-owner')).toContainText(lastName);
    await expect(page.getByTestId('award-recipient')).toHaveCount(0);
    const awardPage = page.url().replace(/\/edit$/, '');

    await page.goto(awardPage);

    await expect(page.getByTestId('award-owner-unclaimed')).toHaveText('Без облікового запису');
    await expect(page.getByTestId('award-edit')).toBeVisible();
    expect(await seriousViolations(page)).toEqual([]);

    await page.goto(personPage);
    await expect(page.getByTestId('person-award-link')).toHaveText(title);
    await page.getByTestId('person-award-link').click();
    await page.getByTestId('award-remove').click();
    await page.getByTestId('confirm-accept').click();
    await expect(page).toHaveURL(/\/awards$/);

    await page.goto(personPage);
    await removeOpenPerson(page);
  });

  test('ac2_1 the colleague search finds an unclaimed person by name', async ({ page }) => {
    const lastName = surname();
    await signIn(page, secretary, DEMO_PASSWORD);
    await page.goto('/admin/users');
    await addPerson(page, lastName, '');
    await expect(page).toHaveURL(/\/admin\/users\/\d+$/);
    const personPage = page.url();
    const personId = personPage.split('/').pop();
    await openNewAward(page);

    await page.getByTestId('award-recipient-colleague').click();
    await page.getByTestId('award-recipient-colleague-search').fill(lastName);
    await page.getByTestId(`colleague-option-${personId}`).click();

    await expect(page.getByTestId('award-recipient-colleague-search')).toHaveValue(
      `${lastName} Petro`,
    );

    await page.goto(personPage);
    await removeOpenPerson(page);
  });

  test('ac1_9 the rector secretary sees no add button', async ({ page }) => {
    await signIn(page, rectorSecretary, DEMO_PASSWORD);
    await page.goto('/admin/users');

    await expect(page.getByTestId('users-table')).toBeVisible();
    await expect(page.getByTestId('person-add-open')).toHaveCount(0);
  });
});

test.describe('users list that splits on select (9.7.5)', () => {
  const { admin, dean } = SEED;

  test('ac5_1 ac5_2 ac5_3 the full table narrows beside the person pane and comes back on close', async ({
    page,
  }) => {
    await page.setViewportSize({ width: 1280, height: 800 });
    await signIn(page, admin, DEMO_PASSWORD);
    await page.goto('/admin/users');
    const table = page.locator('table[data-testid="users-table"]');

    await expect(table.locator('thead th')).toHaveText([
      "Ім'я та адреса",
      'Підрозділ',
      'Ролі',
      'Статус',
      'Останній вхід',
    ]);
    await page.getByTestId('filter-q').fill(admin);
    const own = userTableRow(page, admin);
    await expect(own.getByTestId('user-last-login')).not.toContainText('Ще не входив');
    expect(await seriousViolations(page)).toEqual([]);

    await page.getByTestId('filter-q').fill(dean);
    await userRow(page, dean).click();

    await expect(page).toHaveURL(/\/admin\/users\/\d+$/);
    await expect(table).toHaveCount(0);
    await expect(userRow(page, dean)).toHaveAttribute('aria-current', 'true');
    await expect(userRow(page, dean)).not.toContainText('Декан');
    await expect(page.getByTestId('detail-tabs').getByRole('tab')).toHaveText([
      'Ролі',
      'Обліковий запис',
    ]);
    await expect(page.getByTestId('detail-awards')).toHaveText(/Нагороди · \d+/);
    await expect(page.getByTestId('back-to-users')).toBeHidden();
    expect(
      await page
        .locator('app-split .split__side')
        .evaluate((side) => getComputedStyle(side).position),
    ).toBe('sticky');
    expect(await seriousViolations(page)).toEqual([]);

    await page.getByTestId('detail-tab-account').click();
    await expect(page.getByTestId('detail-last-login')).toBeVisible();

    const selected = page.url();
    await page.reload();
    await expect(page).toHaveURL(selected);
    await expect(page.getByTestId('detail-email')).toHaveText(dean);

    await page.getByTestId('detail-close').click();

    await expect(page).toHaveURL(/\/admin\/users$/);
    await expect(table).toBeVisible();
  });

  test('ac5_3 the delegations link opens the person page read only', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 800 });
    await signIn(page, admin, DEMO_PASSWORD);
    await page.goto('/admin/users');
    await page.getByTestId('filter-q').fill(dean);
    await userRow(page, dean).click();
    const awards = page.getByTestId('detail-awards');
    await expect(awards).toHaveAttribute('href', /\/achievements\?person=\d+$/);

    await page.getByTestId('detail-delegations').click();

    await expect(page).toHaveURL(/\/delegations\?delegatorId=\d+$/);
    await expect(page.getByTestId('page-context')).toHaveText(
      'Повноваження цієї особи: передані та отримані (лише перегляд)',
    );
    await expect(page.getByTestId('delegate-open')).toHaveCount(0);
  });

  test('ac5_4 at 360 the list is full width and a person opens as a full page', async ({
    page,
  }) => {
    await page.setViewportSize({ width: 360, height: 780 });
    await signIn(page, admin, DEMO_PASSWORD);
    await page.goto('/admin/users');
    await page.getByTestId('filter-q').fill(dean);

    await expect(page.locator('table[data-testid="users-table"]')).toHaveCount(0);
    await userRow(page, dean).click();

    await expect(page.getByTestId('back-to-users')).toBeVisible();
    await expect(page.getByTestId('detail-close')).toBeHidden();
    await expect(page.getByTestId('users-table')).toBeHidden();
    expect(
      await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
    ).toBe(true);

    await page.getByTestId('back-to-users').click();
    await expect(page.getByTestId('users-table')).toBeVisible();
  });
});

test.describe('users table rows open the person (9.2.8)', () => {
  const { admin, dean } = SEED;

  test('ac1 ac2 a click on any cell of the row opens the card and the row shows a pointer', async ({
    page,
  }) => {
    await page.setViewportSize({ width: 1280, height: 800 });
    await signIn(page, admin, DEMO_PASSWORD);
    await page.goto('/admin/users');
    await page.getByTestId('filter-q').fill(dean);
    const row = userTableRow(page, dean);

    await expect(row).toHaveCount(1);
    expect(await row.evaluate((element) => getComputedStyle(element).cursor)).toBe('pointer');
    await row.getByTestId('user-roles').click();

    await expect(page).toHaveURL(/\/admin\/users\/\d+$/);
    await expect(userRow(page, dean)).toHaveAttribute('aria-current', 'true');
  });
});
