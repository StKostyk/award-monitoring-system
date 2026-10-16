import { expect, test } from '@playwright/test';

import {
  FRESH_PASSWORD,
  SEED,
  countMessages,
  registerAndVerify,
  seriousViolations,
  signIn,
  sql,
} from './helpers';

function userId(email: string): string {
  return `(select user_id from users where email_address = '${email}')`;
}

function erasureRows(email: string): string {
  return sql(
    `select string_agg(status, ',' order by request_id) from erasure_requests where user_id = ${userId(email)}`,
  ).trim();
}

test.describe('account erasure', () => {
  test('ac4_3 ac4_4 ac4_7 ac4_8 requests the erasure, shows the banner everywhere and cancels it', async ({
    page,
  }) => {
    const email = `e2e.erasure.${Date.now()}@chnu.edu.ua`;
    await registerAndVerify(page, email, FRESH_PASSWORD);
    await signIn(page, email, FRESH_PASSWORD);
    await expect(page.getByTestId('user-name')).toBeVisible();
    await page.goto('/privacy');
    const card = page.getByTestId('privacy-erasure-card');

    await expect(card.getByTestId('privacy-erasure-terms').locator('li')).toHaveCount(4);
    await expect(page.getByTestId('erasure-banner')).toHaveCount(0);

    await card.getByTestId('privacy-erasure-open').click();
    await page.getByTestId('erasure-password').fill('not-my-password');
    await page.getByTestId('erasure-dialog-confirm').click();
    await expect(page.getByRole('dialog')).toContainText('Позначте поле');

    await page.getByTestId('erasure-understood').locator('input').check();
    await page.getByTestId('erasure-dialog-confirm').click();
    await expect(page.getByTestId('erasure-dialog-error')).toHaveText('Неправильний пароль');
    expect(erasureRows(email)).toBe('');
    expect(await seriousViolations(page)).toEqual([]);

    await page.getByTestId('erasure-password').fill(FRESH_PASSWORD);
    await page.getByTestId('erasure-dialog-confirm').click();
    await expect(page.getByRole('dialog')).toHaveCount(0);

    const day = sql(
      `select to_char(scheduled_for at time zone 'Europe/Kyiv', 'DD.MM.YYYY') from erasure_requests ` +
        `where user_id = ${userId(email)} and status = 'PENDING'`,
    ).trim();
    await expect(card.getByTestId('privacy-erasure-scheduled')).toContainText(day);
    await expect(page.getByTestId('erasure-banner')).toContainText(day);
    await expect(page.getByTestId('privacy-activity-action').first()).toHaveText(
      'Запит на видалення облікового запису',
    );
    await expect.poll(() => countMessages(email, 'Account erasure requested')).toBe(1);

    await page.goto('/profile');
    await expect(page.getByTestId('erasure-banner')).toContainText(day);
    await page.getByTestId('erasure-banner-link').click();
    await expect(page).toHaveURL('http://localhost:4200/privacy');

    await card.getByTestId('privacy-erasure-cancel').click();
    await expect(card.getByTestId('privacy-erasure-done')).toHaveText(
      'Видалення скасовано. Обліковий запис залишається.',
    );
    await expect(page.getByTestId('erasure-banner')).toHaveCount(0);
    await expect(card.getByTestId('privacy-erasure-open')).toBeVisible();
    expect(erasureRows(email)).toBe('CANCELLED');
    await expect.poll(() => countMessages(email, 'Account erasure cancelled')).toBe(1);
  });

  test('ac4_3 refuses the erasure of an account under a legal hold', async ({ page }) => {
    const email = `e2e.erasure.held.${Date.now()}@chnu.edu.ua`;
    await registerAndVerify(page, email, FRESH_PASSWORD);
    sql(
      `insert into legal_holds (user_id, reason, placed_by) values (${userId(email)}, ` +
        `'Inspection 2026/14 by the ministry', ${userId(SEED.gdpr)})`,
    );
    await signIn(page, email, FRESH_PASSWORD);
    await expect(page.getByTestId('user-name')).toBeVisible();
    await page.goto('/privacy');

    await page.getByTestId('privacy-erasure-open').click();
    await page.getByTestId('erasure-password').fill(FRESH_PASSWORD);
    await page.getByTestId('erasure-understood').locator('input').check();
    await page.getByTestId('erasure-dialog-confirm').click();

    await expect(page.getByTestId('erasure-dialog-error')).toContainText(
      'через юридичне зобов’язання',
    );
    expect(erasureRows(email)).toBe('');
  });
});
