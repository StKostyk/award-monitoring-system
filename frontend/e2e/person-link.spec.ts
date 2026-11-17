import { Page } from '@playwright/test';

import { expect, test } from './fixtures';

import {
  DEMO_PASSWORD,
  FRESH_PASSWORD,
  SEED,
  registerAndVerify,
  seriousViolations,
  signIn,
  signedInAs,
  uniqueToken,
} from './helpers';

const DEPARTMENT = 'Кафедра алгебри';

/** Adds a person without an address and a draft award for them; returns the person's id. */
async function personWithDraft(page: Page, lastName: string, title: string): Promise<string> {
  await page.goto('/admin/users');
  await page.getByTestId('person-add-open').click();
  await page.getByTestId('person-last-name').fill(lastName);
  await page.getByTestId('person-first-name').fill('Оксана');
  await page.getByTestId('person-department').click();
  await page.getByRole('option', { name: DEPARTMENT }).click();
  await page.getByTestId('person-save').click();
  await expect(page).toHaveURL(/\/admin\/users\/\d+$/);
  const personId = page.url().split('/').pop() ?? '';
  await page.getByTestId('person-add-award').click();
  await page.getByTestId('award-title-uk').fill(title);
  await page.getByTestId('award-save').click();
  await expect(page).toHaveURL(/\/awards\/\d+\/edit$/);
  return personId;
}

test.describe('link at membership confirmation', () => {
  test('ac4_1 ac4_2 ac4_4 the secretary joins colleagues to a newcomer and to a member', async ({
    page,
    browser,
  }) => {
    const token = uniqueToken();
    const email = `link.${token}@chnu.edu.ua`;
    const firstTitle = `Нагорода до приєднання ${token}`;
    const secondTitle = `Друга нагорода до приєднання ${token}`;
    await registerAndVerify(page, email, FRESH_PASSWORD);
    await signIn(page, SEED.secretary, DEMO_PASSWORD);
    const first = await personWithDraft(page, `Перша${token}`, firstTitle);
    const second = await personWithDraft(page, `Друга${token}`, secondTitle);

    await page.goto('/admin/users');
    await page.getByTestId('filter-q').fill(email);
    const row = page.getByRole('row').filter({ hasText: email });
    await row.getByTestId('correct-department').click();

    await expect(page.getByTestId('confirm-candidates')).toBeVisible();
    await expect(page.getByTestId(`confirm-candidate-${first}`)).toContainText('нагород: 1');
    expect(await seriousViolations(page)).toEqual([]);

    await page.getByTestId(`confirm-candidate-${first}`).click();
    await page.getByTestId('confirm-submit').click();

    await expect(row).toContainText('Працівник');

    await row.getByTestId('user-link').click();
    await page.getByTestId('person-link-open').click();
    await expect(page.getByTestId(`confirm-candidate-${first}`)).toHaveCount(0);
    await page.getByTestId(`confirm-candidate-${second}`).click();
    await page.getByTestId('confirm-submit').click();

    await expect(page.getByTestId('person-linked')).toHaveText('Нагороди приєднано: 1.');

    const member = await signedInAs(browser, email, FRESH_PASSWORD);
    await member.goto('/awards');
    await expect(member.getByText(firstTitle, { exact: true })).toBeVisible();
    await expect(member.getByText(secondTitle, { exact: true })).toBeVisible();
    await member.close();
  });
});
