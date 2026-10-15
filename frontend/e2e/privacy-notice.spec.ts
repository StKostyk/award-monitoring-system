import { expect, test } from '@playwright/test';

import { FRESH_PASSWORD, registerAndVerify, seriousViolations, sql, uniqueToken } from './helpers';

test.describe('privacy notice', () => {
  test('ac1_2 ac1_9 shows the current notice in the interface language with its versions', async ({
    page,
  }) => {
    await page.goto('/privacy-notice');
    await expect(page.getByTestId('notice-text')).toContainText('Хто відповідає за обробку');
    await expect(page.getByTestId('notice-text')).toContainText('gdpr@chnu.edu.ua');
    await expect(page.getByTestId('notice-acknowledge')).toHaveCount(0);
    expect(await seriousViolations(page)).toEqual([]);

    await page.getByTestId('notice-version').click();
    await expect(page.getByRole('option')).toHaveCount(1);
    await expect(page.getByRole('option')).toContainText('1.0 · 15.10.2026');
    await page.keyboard.press('Escape');

    await page.getByTestId('language-toggle').click();
    await expect(page.getByTestId('notice-text')).toContainText('Who is responsible');
  });

  test('ac1_8 a person without an acknowledgement of the current version confirms it and returns', async ({
    page,
  }) => {
    const email = `e2e.notice.${uniqueToken()}@chnu.edu.ua`;
    const userId = `(select user_id from users where email_address = '${email}')`;
    await registerAndVerify(page, email, FRESH_PASSWORD);
    sql(
      `begin; set local session_replication_role = replica; delete from consent_records where user_id = ${userId}; commit;`,
    );

    await page.goto('/notifications');
    await expect(page).toHaveURL(/localhost:8080\/login/);
    await page.fill('#username', email);
    await page.fill('#password', FRESH_PASSWORD);
    await page.click('button[type="submit"]');

    await expect(page).toHaveURL(/\/privacy-notice\?acknowledge=true&returnUrl=%2Fnotifications/);
    await expect(page.getByTestId('notice-acknowledge-hint')).toContainText('1.0');
    await expect(page.getByTestId('notice-text')).toContainText('Хто відповідає за обробку');
    expect(await seriousViolations(page)).toEqual([]);
    await page.getByTestId('notice-acknowledge').click();

    await expect(page).toHaveURL(/\/notifications$/);
    await page.goto('/');
    await expect(page.getByTestId('user-name')).toHaveText('Ірина Нова');
    await expect(page).not.toHaveURL(/privacy-notice/);
    expect(
      sql(
        `select count(*) from consent_records where consent_type = 'DATA_PROCESSING' and user_id = ${userId}`,
      ),
    ).toBe('1');
  });
});
