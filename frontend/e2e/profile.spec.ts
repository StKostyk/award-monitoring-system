import { readFileSync } from 'node:fs';

import { expect, test } from '@playwright/test';

import { countMessages, linkFor, registerAndVerify, signIn } from './helpers';

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

  test('ac17 f3 a link opened where another user is signed in keeps that session', async ({ page, browser }) => {
    const stamp = Date.now();
    const mover = `e2e.lender.${stamp}@chnu.edu.ua`;
    const moved = `e2e.lent.${stamp}@chnu.edu.ua`;
    const host = `e2e.host.${stamp}@chnu.edu.ua`;
    await registerAndVerify(page, mover, PASSWORD);
    await signIn(page, mover, PASSWORD);
    await expect(page.getByTestId('user-name')).toBeVisible();
    await page.goto('/profile');
    await page.getByTestId('profile-change-address').click();
    await page.getByTestId('email-change-new').fill(moved);
    await page.getByTestId('email-change-password').fill(PASSWORD);
    await page.getByTestId('email-change-submit').click();
    await expect(page.getByTestId('profile-link-sent')).toContainText(moved);

    const other = await (await browser.newContext()).newPage();
    await registerAndVerify(other, host, PASSWORD);
    await signIn(other, host, PASSWORD);
    await expect(other.getByTestId('user-name')).toBeVisible();
    await other.goto(await linkFor(moved, 'confirm-email-change'));
    await expect(other.getByTestId('confirm-email-changed')).toContainText(moved);

    await other.goto('/profile');
    await expect(other.getByTestId('profile-email')).toHaveText(host);
    await other.context().close();
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

  test('ac31 ac33 ac34 ac35 downloads my data once a minute and announces it by email', async ({ page }) => {
    const email = `e2e.export.${Date.now()}@chnu.edu.ua`;
    await registerAndVerify(page, email, PASSWORD);
    await signIn(page, email, PASSWORD);
    await expect(page.getByTestId('user-name')).toBeVisible();
    await page.goto('/profile');
    await expect(page.getByTestId('profile-my-data')).toBeVisible();

    const downloading = page.waitForEvent('download');
    await page.getByTestId('profile-download').click();
    const download = await downloading;
    expect(download.suggestedFilename()).toMatch(/^award-monitoring-export-\d{4}-\d{2}-\d{2}\.json$/);
    const path = await download.path();
    const body = readFileSync(path, 'utf-8');
    const file = JSON.parse(body) as { personal_data: { profile: { email: string } }; roles: unknown[] };
    expect(file.personal_data.profile.email).toBe(email);
    expect(file.roles).toEqual([]);
    expect(body).not.toMatch(/password|\$2a\$|token/);
    await expect(page.getByTestId('profile-export-done')).toBeVisible();

    await page.getByTestId('profile-download').click();
    await expect(page.getByTestId('profile-export-error')).toHaveText('Забагато запитів. Спробуйте пізніше.');
    await expect.poll(() => countMessages(email, 'Your data was exported')).toBe(1);
  });
});
