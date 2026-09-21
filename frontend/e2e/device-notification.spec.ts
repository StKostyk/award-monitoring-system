import { expect, test } from '@playwright/test';

import { countMessages, linkFor, registerAndVerify, signIn } from './helpers';

const firefoxOnMac = 'Mozilla/5.0 (Macintosh; Intel Mac OS X 14.6; rv:130.0) Gecko/20100101 Firefox/130.0';

test.describe('new device notification', () => {
  test('ac51 ac52 ac53 ac54 announces an unknown browser once and the not-me link ends every session', async ({
    page,
    browser,
  }) => {
    const email = `e2e.device.${Date.now()}@chnu.edu.ua`;
    const password = 'correct-horse-battery';
    const newPassword = 'staple-battery-horse';
    await registerAndVerify(page, email, password);

    await signIn(page, email, password);
    await expect(page.getByTestId('user-name')).toHaveText('Ірина Нова');
    expect(await linkFor(email, 'security/not-me')).toContain('/security/not-me?token=');

    await page.getByTestId('logout').click();
    await expect(page).toHaveURL(/localhost:8080\/login/);
    await signIn(page, email, password);
    await expect(page.getByTestId('user-name')).toHaveText('Ірина Нова');
    await page.waitForTimeout(2_000);
    expect(await countMessages(email, 'New sign-in')).toBe(1);

    const other = await browser.newContext({ userAgent: firefoxOnMac, locale: 'en-US' });
    const otherPage = await other.newPage();
    await signIn(otherPage, email, password);
    await expect(otherPage.getByTestId('user-name')).toHaveText('Ірина Нова');
    const link = await linkFor(email, 'security/not-me', 2);

    await page.goto(link);
    await expect(page.locator('mat-card-title')).toHaveText('Це був ваш вхід?');
    await page.getByTestId('language-toggle').click();
    await expect(page.locator('mat-card-title')).toHaveText('Was this sign-in yours?');
    await page.getByTestId('not-me-confirm').click();
    await expect(page.getByTestId('not-me-done')).toContainText('1 hour');

    await page.goto(link);
    await page.getByTestId('not-me-confirm').click();
    await expect(page.getByTestId('not-me-invalid')).toBeVisible();
    await page.getByTestId('not-me-go-forgot').click();
    await expect(page).toHaveURL(/forgot-password/);

    await otherPage.evaluate(() => sessionStorage.clear());
    await otherPage.goto('/');
    await expect(otherPage).toHaveURL(/localhost:8080\/login/);
    await otherPage.fill('#username', email);
    await otherPage.fill('#password', password);
    await otherPage.click('button[type="submit"]');
    await expect(otherPage).toHaveURL(/error=BAD_CREDENTIALS/);
    await other.close();

    await page.goto(await linkFor(email, 'reset-password'));
    await page.getByTestId('reset-password').fill(newPassword);
    await page.getByTestId('reset-submit').click();
    await expect(page.getByTestId('reset-done')).toBeVisible();

    await page.evaluate(() => sessionStorage.clear());
    await signIn(page, email, newPassword);
    await expect(page.getByTestId('user-name')).toHaveText('Ірина Нова');
  });
});
