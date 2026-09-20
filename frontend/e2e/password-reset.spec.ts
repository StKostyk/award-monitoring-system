import { expect, test } from '@playwright/test';

const mailpit = 'http://localhost:8025';

async function linkFor(email: string, path: string): Promise<string> {
  const pattern = new RegExp(`http://localhost:4200/${path}\\?token=[A-Za-z0-9_-]+`);
  for (let attempt = 0; attempt < 40; attempt++) {
    const list = await (await fetch(`${mailpit}/api/v1/messages?limit=50`)).json();
    const message = list.messages?.find((m: { To: { Address: string }[] }) =>
      m.To?.some((to) => to.Address.toLowerCase() === email.toLowerCase()),
    );
    if (message) {
      const full = await (await fetch(`${mailpit}/api/v1/message/${message.ID}`)).json();
      const match = pattern.exec(full.Text ?? '');
      if (match) {
        return match[0];
      }
    }
    await new Promise((resolve) => setTimeout(resolve, 500));
  }
  throw new Error(`No ${path} email for ${email}`);
}

async function signIn(page: import('@playwright/test').Page, email: string, password: string): Promise<void> {
  await page.goto('/');
  await expect(page).toHaveURL(/localhost:8080\/login/);
  await page.fill('#username', email);
  await page.fill('#password', password);
  await page.click('button[type="submit"]');
}

test.describe('password reset', () => {
  test('ac31 ac32 ac33 ac34 resets the password from the email link and signs in with the new one', async ({ page }) => {
    const email = `e2e.reset.${Date.now()}@chnu.edu.ua`;
    const oldPassword = 'correct-horse-battery';
    const newPassword = 'staple-battery-horse';

    await fetch('http://localhost:8080/api/v1/auth/register', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password: oldPassword, firstName: 'Ірина', lastName: 'Нова', organizationId: 64 }),
    });
    await page.goto(await linkFor(email, 'verify-email'));
    await page.getByTestId('verify-password').fill(oldPassword);
    await page.getByTestId('verify-submit').click();
    await expect(page.getByTestId('verify-success')).toContainText(email);

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
