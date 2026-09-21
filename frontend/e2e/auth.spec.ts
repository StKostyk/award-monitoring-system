import { expect, test } from '@playwright/test';

import { signIn } from './helpers';

const employee = { email: 'employee.fmi@chnu.edu.ua', password: 'Passw0rd-demo', name: 'Анастасія Працівник' };

test.describe('authentication', () => {
  test('ac11 ac12 ac18 signs in through the authorization server and out again', async ({ page }) => {
    await page.goto('/');

    await expect(page).toHaveURL(/localhost:8080\/login/);
    await expect(page.locator('html')).toHaveAttribute('lang', 'uk');
    await page.fill('#username', employee.email);
    await page.fill('#password', employee.password);
    await page.click('button[type="submit"]');

    await expect(page).toHaveURL('http://localhost:4200/');
    await expect(page.getByTestId('user-name')).toHaveText(employee.name);
    await expect(page.getByTestId('profile-email')).toHaveText(employee.email);
    await expect(page.getByTestId('profile-roles')).toContainText('Працівник');

    await page.getByTestId('logout').click();

    await expect(page).toHaveURL(/localhost:8080\/login/);
  });

  test('ac16 a pending account is refused with an explanation', async ({ page }) => {
    await signIn(page, 'pending@chnu.edu.ua', employee.password);

    await expect(page).toHaveURL(/error=PENDING/);
    await expect(page.locator('.alert')).toContainText('не підтверджено');
  });

  test('ac17 the login page switches to English and the app follows the toggle', async ({ page }) => {
    await page.goto('/');
    await page.click('.card__lang');
    await expect(page.locator('html')).toHaveAttribute('lang', 'en');
    await expect(page.locator('h1')).toHaveText('Sign in');

    await page.fill('#username', employee.email);
    await page.fill('#password', employee.password);
    await page.click('button[type="submit"]');
    await expect(page.getByTestId('user-name')).toHaveText(employee.name);

    await page.getByTestId('language-toggle').click();
    await expect(page.getByTestId('profile-roles')).toContainText('Employee');
  });
});
