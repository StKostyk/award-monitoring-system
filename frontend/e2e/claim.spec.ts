import { expect, test } from './fixtures';

import {
  DEMO_PASSWORD,
  FRESH_PASSWORD,
  SEED,
  linkFor,
  register,
  seriousViolations,
  signIn,
  signedInAs,
  uniqueToken,
} from './helpers';

const DEPARTMENT = 'Кафедра алгебри';

test.describe('claim at registration', () => {
  test('ac3_1 ac3_2 ac3_3 a colleague registers and finds the awards entered for them', async ({
    page,
    browser,
  }) => {
    const token = uniqueToken();
    const lastName = `Test${token.charAt(0).toUpperCase()}${token.substring(1)}`;
    const email = `${lastName.toLowerCase()}@chnu.edu.ua`;
    const title = `Грамота до реєстрації ${token}`;
    await signIn(page, SEED.secretary, DEMO_PASSWORD);
    await page.goto('/admin/users');
    await page.getByTestId('person-add-open').click();
    await page.getByTestId('person-last-name').fill(lastName);
    await page.getByTestId('person-first-name').fill('Petro');
    await page.getByTestId('person-department').click();
    await page.getByRole('option', { name: DEPARTMENT }).click();
    await page.getByTestId('person-email').fill(email);
    await page.getByTestId('person-save').click();
    await expect(page).toHaveURL(/\/admin\/users\/\d+$/);
    const personPage = page.url();
    await page.getByTestId('person-add-award').click();
    await page.getByTestId('award-title-uk').fill(title);
    await page.getByTestId('award-save').click();
    await expect(page).toHaveURL(/\/awards\/\d+\/edit$/);

    await register(email, FRESH_PASSWORD);
    const context = await browser.newContext();
    const person = await context.newPage();
    await person.goto(await linkFor(email, 'verify-email'));
    await person.getByTestId('verify-password').fill(FRESH_PASSWORD);
    await person.getByTestId('verify-submit').click();

    await expect(person.getByTestId('verify-success')).toContainText(
      `Адресу ${email} підтверджено. На вас уже чекають нагороди: 1.`,
    );
    expect(await seriousViolations(person)).toEqual([]);
    await context.close();

    const claimed = await signedInAs(browser, email, FRESH_PASSWORD);
    await claimed.goto('/awards');
    await expect(claimed.getByText(title)).toBeVisible();
    await claimed.close();

    await page.goto(personPage);
    await expect(page.getByTestId('person-add-award')).toHaveCount(0);
  });
});
