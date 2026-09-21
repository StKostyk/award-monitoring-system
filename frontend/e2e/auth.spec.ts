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

  test('ac61 an expired session ends at the login page, not on a blank screen', async ({ page, context }) => {
    await signIn(page, employee.email, employee.password);
    await expect(page.getByTestId('user-name')).toHaveText(employee.name);

    await page.evaluate(() => {
      sessionStorage.setItem('expires_at', String(Date.now() - 2 * 60_000));
      sessionStorage.setItem('access_token', 'expired.access.token');
      sessionStorage.setItem('refresh_token', 'revoked-refresh-token');
    });
    await context.clearCookies();
    await page.reload();

    await expect(page).toHaveURL(/localhost:8080\/login/);
    await expect(page.locator('h1')).toHaveText('Вхід');
  });

  test('ac61 ac65 a refresh token revoked elsewhere sends the open tab to the login page', async ({ page, context }) => {
    await signIn(page, employee.email, employee.password);
    await expect(page.getByTestId('user-name')).toHaveText(employee.name);

    const refreshToken = await page.evaluate(() => sessionStorage.getItem('refresh_token'));
    const revoked = await page.request.post('http://localhost:8080/oauth2/revoke', {
      form: { token: refreshToken ?? '', token_type_hint: 'refresh_token', client_id: 'award-web' },
    });
    expect(revoked.ok()).toBeTruthy();
    await context.clearCookies();
    await page.evaluate(() => sessionStorage.setItem('expires_at', String(Date.now() + 4000)));
    await page.reload();

    await expect(page).toHaveURL(/localhost:8080\/login/, { timeout: 15_000 });
    await expect(page.locator('h1')).toHaveText('Вхід');
  });

  test('ac63 signing in at the login page directly lands on the app', async ({ page }) => {
    await page.goto('http://localhost:8080/login');
    await page.fill('#username', employee.email);
    await page.fill('#password', employee.password);
    await page.click('button[type="submit"]');

    await expect(page).toHaveURL('http://localhost:4200/');
    await expect(page.getByTestId('user-name')).toHaveText(employee.name);

    await page.goto('http://localhost:8080/login');
    await expect(page).toHaveURL('http://localhost:4200/');
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
