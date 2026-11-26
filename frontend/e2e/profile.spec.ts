import AxeBuilder from '@axe-core/playwright';
import { expect, test } from './fixtures';

import { freshContextPage, linkFor, registerAndVerify, signIn } from './helpers';

const PASSWORD = 'correct-horse-battery';

test.describe('profile', () => {
  test('ac11 ac12 ac13 ac17 shows the profile and renames the owner with the header following', async ({
    page,
  }) => {
    const email = `e2e.rename.${Date.now()}@chnu.edu.ua`;
    await registerAndVerify(page, email, PASSWORD);
    await signIn(page, email, PASSWORD);
    await expect(page.getByTestId('user-name')).toHaveText('Ірина Нова');

    await page.getByTestId('user-menu').click();
    await page.getByTestId('nav-profile').click();
    await expect(page).toHaveURL('http://localhost:4200/profile');
    await expect(page.getByTestId('profile-email')).toHaveText(email);
    await expect(page.getByTestId('profile-section-names')).toHaveAttribute('aria-current', 'true');
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

    await page.getByTestId('profile-section-roles').click();
    await expect(page).toHaveURL('http://localhost:4200/profile?section=roles');
    await expect(page.getByTestId('profile-department')).toContainText('Кафедра');
    await expect(page.getByTestId('profile-faculty')).toBeVisible();
    await expect(page.getByTestId('profile-no-roles')).toBeVisible();
    await expect(page.getByTestId('profile-names')).toHaveCount(0);
  });

  test('ac14 ac15 ac16 ac17 moves the account to a confirmed address and signs it out', async ({
    page,
  }) => {
    const stamp = Date.now();
    const email = `e2e.mover.${stamp}@chnu.edu.ua`;
    const moved = `e2e.moved.${stamp}@chnu.edu.ua`;
    await registerAndVerify(page, email, PASSWORD);
    await signIn(page, email, PASSWORD);
    await expect(page.getByTestId('user-name')).toBeVisible();
    await page.goto('/profile?section=address');

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

  test('ac17 f3 a link opened where another user is signed in keeps that session', async ({
    page,
    browser,
  }) => {
    const stamp = Date.now();
    const mover = `e2e.lender.${stamp}@chnu.edu.ua`;
    const moved = `e2e.lent.${stamp}@chnu.edu.ua`;
    const host = `e2e.host.${stamp}@chnu.edu.ua`;
    await registerAndVerify(page, mover, PASSWORD);
    await signIn(page, mover, PASSWORD);
    await expect(page.getByTestId('user-name')).toBeVisible();
    await page.goto('/profile?section=address');
    await page.getByTestId('profile-change-address').click();
    await page.getByTestId('email-change-new').fill(moved);
    await page.getByTestId('email-change-password').fill(PASSWORD);
    await page.getByTestId('email-change-submit').click();
    await expect(page.getByTestId('profile-link-sent')).toContainText(moved);

    const other = await freshContextPage(browser);
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

  test('ac3_6 ac6_3 keeps one channel per type and stores each switch', async ({ page }) => {
    const email = `e2e.prefs.${Date.now()}@chnu.edu.ua`;
    await registerAndVerify(page, email, PASSWORD);
    await signIn(page, email, PASSWORD);

    await page.goto('/profile#notifications');
    const card = page.getByTestId('notification-preferences');
    await expect(card).toBeInViewport();
    await expect(page.getByTestId('profile-section-notifications')).toHaveAttribute(
      'aria-current',
      'true',
    );
    await expect(card.getByTestId('preferences-group')).toHaveCount(3);
    await expect(card.getByTestId('preferences-security')).toContainText('надсилаються завжди');
    await expect(card.getByTestId('preferences-save')).toHaveCount(0);
    const scan = await new AxeBuilder({ page })
      .include('[data-testid="notification-preferences"]')
      .analyze();
    expect(scan.violations.map((violation) => violation.id)).toEqual([]);

    const awardEmail = page.getByRole('switch', { name: 'Е-пошта: Нагороду погоджено' });
    const awardInApp = page.getByRole('switch', { name: 'У системі: Нагороду погоджено' });
    const overdueInApp = page.getByRole('switch', {
      name: 'У системі: Щоденний перелік прострочених розглядів',
    });
    const saved = page.waitForResponse(
      (response) =>
        response.url().endsWith('/users/me/notification-preferences') &&
        response.request().method() === 'PUT',
    );
    await awardEmail.click();
    await saved;
    await expect(card.getByTestId('preferences-saved')).toHaveText(
      'Налаштування сповіщень збережено',
    );
    await expect(awardEmail).not.toBeChecked();

    await awardInApp.click();
    await expect(awardInApp).toBeChecked();
    await expect(card.getByTestId('preferences-error')).toContainText('останній увімкнений');

    const second = page.waitForResponse(
      (response) =>
        response.url().endsWith('/users/me/notification-preferences') &&
        response.request().method() === 'PUT',
    );
    await overdueInApp.click();
    await second;

    await page.goto('/profile?section=notifications');
    await page.reload();
    await expect(awardEmail).not.toBeChecked();
    await expect(awardInApp).toBeChecked();
    await expect(overdueInApp).not.toBeChecked();
  });
});
