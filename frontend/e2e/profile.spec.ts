import { expect, test } from '@playwright/test';

import { linkFor, registerAndVerify, signIn } from './helpers';

const PASSWORD = 'correct-horse-battery';

test.describe('profile', () => {
  test('ac11 ac12 ac13 ac17 shows the profile and renames the owner with the header following', async ({ page }) => {
    const email = `e2e.rename.${Date.now()}@chnu.edu.ua`;
    await registerAndVerify(page, email, PASSWORD);
    await signIn(page, email, PASSWORD);
    await expect(page.getByTestId('user-name')).toHaveText('Ірина Нова');

    await page.getByTestId('user-menu').click();
    await page.getByTestId('nav-profile').click();
    await expect(page).toHaveURL('http://localhost:4200/profile');
    await expect(page.getByTestId('profile-email')).toHaveText(email);
    await expect(page.getByTestId('profile-department')).toContainText('Кафедра');
    await expect(page.getByTestId('profile-faculty')).toBeVisible();
    await expect(page.getByTestId('profile-no-roles')).toBeVisible();
    await expect(page.getByTestId('profile-save')).toBeDisabled();

    await page.getByTestId('profile-last-name').fill('Нова1');
    await page.getByTestId('profile-first-name').click();
    await expect(page.getByTestId('profile-last-name-error')).toBeVisible();
    await expect(page.getByTestId('profile-save')).toBeDisabled();

    await page.getByTestId('profile-last-name').fill('Петренко-Коваль');
    await page.getByTestId('profile-save').click();
    await expect(page.getByTestId('profile-saved')).toHaveText('Збережено');
    await expect(page.getByTestId('user-name')).toHaveText('Ірина Петренко-Коваль');
    await expect(page.getByTestId('profile-save')).toBeDisabled();
  });

  test('ac14 ac15 ac16 ac17 moves the account to a confirmed address and signs it out', async ({ page }) => {
    const stamp = Date.now();
    const email = `e2e.mover.${stamp}@chnu.edu.ua`;
    const moved = `e2e.moved.${stamp}@chnu.edu.ua`;
    await registerAndVerify(page, email, PASSWORD);
    await signIn(page, email, PASSWORD);
    await expect(page.getByTestId('user-name')).toBeVisible();
    await page.goto('/profile');

    await page.getByTestId('profile-change-address').click();
    await page.getByTestId('email-change-new').fill(moved);
    await page.getByTestId('email-change-password').fill('wrong-password');
    await page.getByTestId('email-change-submit').click();
    await expect(page.getByTestId('email-change-error')).toHaveText('Невірний пароль');
    await page.getByTestId('email-change-password').fill(PASSWORD);
    await page.getByTestId('email-change-submit').click();
    await expect(page.getByTestId('profile-link-sent')).toContainText(moved);

    const link = await linkFor(moved, 'confirm-email-change');
    await page.goto(link);
    await expect(page.getByTestId('confirm-email-changed')).toContainText(moved);

    await page.goto(link);
    await expect(page.getByTestId('confirm-email-invalid')).toBeVisible();

    await signIn(page, email, PASSWORD);
    await expect(page).toHaveURL(/error=BAD_CREDENTIALS/);
    await signIn(page, moved, PASSWORD);
    await expect(page.getByTestId('user-name')).toBeVisible();
    await page.goto('/profile');
    await expect(page.getByTestId('profile-email')).toHaveText(moved);
  });

  test('ac17 the profile needs a session and returns to it after signing in', async ({ page }) => {
    const email = `e2e.guard.${Date.now()}@chnu.edu.ua`;
    await registerAndVerify(page, email, PASSWORD);
    await page.goto('/profile');
    await expect(page).toHaveURL(/localhost:8080\/login/);
    await page.fill('#username', email);
    await page.fill('#password', PASSWORD);
    await page.click('button[type="submit"]');
    await expect(page).toHaveURL('http://localhost:4200/profile');
    await expect(page.getByTestId('profile-email')).toHaveText(email);
  });
});
