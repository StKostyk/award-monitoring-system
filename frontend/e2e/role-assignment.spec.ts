import { expect, test } from '@playwright/test';

import { registerAndVerify, signIn } from './helpers';

const password = 'correct-horse-battery';
const demo = 'Passw0rd-demo';
const secretary = 'secretary.fmi@chnu.edu.ua';
const dean = 'dean.fmi@chnu.edu.ua';
const employee = 'employee.fmi@chnu.edu.ua';

test.describe('role assignment', () => {
  test('ac26 a newcomer keeps the membership banner and cannot open the directory', async ({ page }) => {
    const email = `newcomer.${Date.now()}@chnu.edu.ua`;
    await registerAndVerify(page, email, password);

    await signIn(page, email, password);

    await expect(page.getByTestId('membership-banner')).toContainText(
      'Ваше членство у підрозділі ще не підтверджено',
    );
    await expect(page.getByTestId('membership-banner')).toContainText('Кафедра алгебри');
    await expect(page.getByTestId('nav-users')).toHaveCount(0);

    await page.goto('/admin/users');

    await expect(page).toHaveURL(/\/forbidden$/);
    await expect(page.getByTestId('forbidden-card')).toContainText('Доступ заборонено');
  });

  test('ac27 ac210 the secretary confirms a newcomer from the user directory', async ({ page }) => {
    const email = `confirm.${Date.now()}@chnu.edu.ua`;
    await registerAndVerify(page, email, password);

    await signIn(page, secretary, demo);
    await page.getByTestId('nav-users').click();

    await expect(page).toHaveURL(/\/admin\/users$/);
    await page.getByTestId('filter-q').fill(email);
    const row = page.getByRole('row').filter({ hasText: email });

    await expect(row.getByTestId('unconfirmed-badge')).toContainText('Не підтверджено');

    await row.getByTestId('confirm-membership').click();

    await expect(row).toContainText('Працівник');
    await expect(row.getByTestId('unconfirmed-badge')).toHaveCount(0);
  });

  test('ac210 the dean assigns a role and revokes it after the confirmation', async ({ page }) => {
    await signIn(page, dean, demo);
    await page.goto('/admin/users');
    await page.getByTestId('filter-q').fill(employee);
    await page.getByRole('row').filter({ hasText: employee }).getByTestId('user-link').click();

    await expect(page.getByTestId('detail-email')).toHaveText(employee);

    await page.getByTestId('assign-role-open').click();
    await page.getByTestId('assign-role').click();
    await page.getByRole('option', { name: 'Секретар факультету' }).click();
    await page.getByTestId('assign-organization').click();
    await page.getByRole('option', { name: 'Факультет математики' }).click();
    await page.getByTestId('assign-submit').click();

    await expect(page.getByTestId('detail-message')).toContainText('Роль призначено');
    await expect(page.getByTestId('detail-session-hint')).toContainText('наступного');
    await expect(page.getByTestId('current-roles')).toContainText('Секретар факультету');

    await page
      .getByRole('listitem')
      .filter({ hasText: 'Секретар факультету' })
      .getByTestId('revoke-role')
      .click();

    await expect(page.getByTestId('revoke-text')).toContainText('Секретар факультету');
    await page.getByTestId('revoke-cancel').click();

    await expect(page.getByTestId('current-roles')).toContainText('Секретар факультету');

    await page
      .getByRole('listitem')
      .filter({ hasText: 'Секретар факультету' })
      .getByTestId('revoke-role')
      .click();
    await page.getByTestId('revoke-confirm').click();

    await expect(page.getByTestId('detail-message')).toContainText('Роль відкликано');
    await expect(page.getByTestId('role-history')).toContainText('Секретар факультету');
  });

  test('ac210 an unknown user ends on the not-found state without an error loop', async ({ page }) => {
    const calls: string[] = [];
    page.on('request', (request) => {
      if (request.url().includes('/api/v1/users/999999')) {
        calls.push(request.url());
      }
    });
    await signIn(page, dean, demo);

    await page.goto('/admin/users/999999');

    await expect(page.getByTestId('detail-not-found')).toContainText('Користувача не знайдено');
    await expect(page.getByTestId('detail-profile')).toHaveCount(0);
    expect(calls).toHaveLength(1);
  });

  test('ac210 the directory is closed to an employee and reads in English too', async ({ page }) => {
    await signIn(page, employee, demo);

    await expect(page.getByTestId('nav-users')).toHaveCount(0);

    await page.goto('/admin/users');

    await expect(page).toHaveURL(/\/forbidden$/);

    await page.getByTestId('language-toggle').click();

    await expect(page.getByTestId('forbidden-card')).toContainText('Access denied');
  });
});
