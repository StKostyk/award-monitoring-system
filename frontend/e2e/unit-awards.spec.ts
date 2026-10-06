import { expect, test } from '@playwright/test';

import {
  FRESH_PASSWORD,
  SEED,
  freshEmployee,
  pastDay,
  seriousViolations,
  signedIn,
  signedInAs,
  sql,
  submitWithoutDocuments,
  uniqueToken,
} from './helpers';

const FACULTY = 9;
const DEPARTMENT = 64;

test.describe('awards of a faculty or department', () => {
  let secretary: string;

  test.beforeAll(async ({ browser }) => {
    secretary = await freshEmployee(browser, 'unit');
    sql(
      `insert into user_roles (user_id, organization_id, role_type) select user_id, ${FACULTY}, 'FACULTY_SECRETARY' from users where email_address = '${secretary}'`,
    );
  });

  test.afterAll(() => {
    sql(
      `delete from user_roles where role_type = 'FACULTY_SECRETARY' and user_id = (select user_id from users where email_address = '${secretary}')`,
    );
  });

  test('ac0_1 ac0_2 ac0_7 ac0_8 a secretary enters a department award and submits it', async ({
    browser,
  }) => {
    const page = await signedInAs(browser, secretary, FRESH_PASSWORD);
    const unitName = sql(`select name_uk from organizations where org_id = ${DEPARTMENT}`);
    const title = `Грамота кафедрі ${uniqueToken()}`;
    await page.goto('/awards/new');

    await expect(page.getByTestId('award-recipient')).toBeVisible();
    await expect(page.getByTestId('award-recipient-picker')).toHaveCount(0);
    await page.getByTestId('award-recipient-unit').click();
    await page.getByTestId('award-recipient-picker').click();
    await expect(page.getByRole('option').first()).toHaveAttribute(
      'data-testid',
      `recipient-option-${FACULTY}`,
    );
    await page.getByTestId(`recipient-option-${DEPARTMENT}`).click();
    expect(await seriousViolations(page)).toEqual([]);

    await page.getByTestId('award-title-uk').fill(title);
    await page.getByTestId('award-category').click();
    await page.getByTestId('category-option-13').click();
    await page.getByTestId('award-organization').fill('Міністерство освіти і науки України');
    await page.getByTestId('award-date').fill(pastDay());
    await submitWithoutDocuments(page);
    await expect(page.getByTestId('award-submitted-name')).toContainText(title);

    const id = sql(`select award_id from awards where title_uk = '${title}'`);
    expect(sql(`select recipient_org_id from awards where award_id = ${id}`)).toBe(
      String(DEPARTMENT),
    );
    await page.goto(`/awards/${id}`);
    await expect(page.getByTestId('award-detail-recipient')).toContainText(unitName);
    await page.goto('/awards');
    const item = page.getByTestId('award-item').filter({ hasText: title });
    await expect(item.getByTestId('award-unit')).toContainText(unitName);
  });

  test('ac0_8 switching back to «Я» hides the picker and saves a personal draft', async ({
    browser,
  }) => {
    const page = await signedInAs(browser, secretary, FRESH_PASSWORD);
    const title = `Особиста чернетка ${uniqueToken()}`;
    await page.goto('/awards/new');
    await page.getByTestId('award-recipient-unit').click();
    await page.getByTestId('award-recipient-picker').click();
    await page.getByTestId(`recipient-option-${DEPARTMENT}`).click();
    await page.getByTestId('award-recipient-person').click();

    await expect(page.getByTestId('award-recipient-picker')).toHaveCount(0);
    await page.getByTestId('award-title-uk').fill(title);
    await page.getByTestId('award-save').click();
    await expect(page.getByTestId('award-form-message')).toBeVisible();
    expect(sql(`select recipient_org_id is null from awards where title_uk = '${title}'`)).toBe(
      't',
    );
  });

  test('ac0_8 an employee is offered no recipient choice', async ({ browser }) => {
    const page = await signedIn(browser, SEED.employee);
    await page.goto('/awards/new');

    await expect(page.getByTestId('award-title-uk')).toBeVisible();
    await expect(page.getByTestId('award-recipient')).toHaveCount(0);
  });
});
