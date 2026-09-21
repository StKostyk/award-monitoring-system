import { expect, test } from '@playwright/test';

import { linkFor, registerAndVerify, signIn } from './helpers';

test.describe('password reset', () => {
  test('ac31 ac32 ac33 ac34 resets the password from the email link and signs in with the new one', async ({ page }) => {
    const email = `e2e.reset.${Date.now()}@chnu.edu.ua`;
    const oldPassword = 'correct-horse-battery';
    const newPassword = 'staple-battery-horse';

    await registerAndVerify(page, email, oldPassword);

    await page.goto('/');
    await expect(page).toHaveURL(/localhost:8080\/login/);
    await page.click('#forgot-password');
    await expect(page).toHaveURL('http://localhost:4200/forgot-password');
    await page.getByTestId('forgot-email').fill(email);
    await page.getByTestId('forgot-submit').click();
    await expect(page.getByTestId('forgot-sent')).toContainText('1 годину');

    const link = await linkFor(email, 'reset-password');
    await page.goto(link);
    await page.getByTestId('reset-password').fill('short');
    await page.getByTestId('reset-submit').click();
    await expect(page.locator('mat-error')).toContainText('10 символів');
    await page.getByTestId('reset-password').fill(newPassword);
    await page.getByTestId('reset-submit').click();
    await expect(page.getByTestId('reset-done')).toBeVisible();

    await page.goto(link);
    await page.getByTestId('reset-password').fill(newPassword);
    await page.getByTestId('reset-submit').click();
    await expect(page.getByTestId('reset-invalid')).toBeVisible();
    await page.getByTestId('reset-go-forgot').click();
    await expect(page).toHaveURL(/forgot-password/);

    await signIn(page, email, oldPassword);
    await expect(page).toHaveURL(/error=BAD_CREDENTIALS/);

    await signIn(page, email, newPassword);
    await expect(page.getByTestId('user-name')).toHaveText('Ірина Нова');
  });

  test('ac31 an unknown address gets the same neutral confirmation', async ({ page }) => {
    await page.goto('/forgot-password');
    await page.getByTestId('forgot-email').fill(`nobody.${Date.now()}@chnu.edu.ua`);
    await page.getByTestId('forgot-submit').click();
    await expect(page.getByTestId('forgot-sent')).toBeVisible();
  });
});
