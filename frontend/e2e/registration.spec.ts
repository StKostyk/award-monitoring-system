import { expect, test } from '@playwright/test';

const mailpit = 'http://localhost:8025';
const password = 'correct-horse-battery';

async function verificationLink(email: string): Promise<string> {
  for (let attempt = 0; attempt < 40; attempt++) {
    const list = await (await fetch(`${mailpit}/api/v1/messages?limit=50`)).json();
    const message = list.messages?.find((m: { To: { Address: string }[] }) =>
      m.To?.some((to) => to.Address.toLowerCase() === email.toLowerCase()),
    );
    if (message) {
      const full = await (await fetch(`${mailpit}/api/v1/message/${message.ID}`)).json();
      const match = /http:\/\/localhost:4200\/verify-email\?token=[A-Za-z0-9_-]+/.exec(full.Text ?? '');
      if (match) {
        return match[0];
      }
    }
    await new Promise((resolve) => setTimeout(resolve, 500));
  }
  throw new Error(`No verification email for ${email}`);
}

test.describe('registration', () => {
  test('ac21 ac25 ac28 registers, verifies the address from the email and signs in', async ({ page }) => {
    const email = `e2e.${Date.now()}@chnu.edu.ua`;

    const departments = page.waitForResponse((response) => response.url().includes('/api/v1/organizations'));
    await page.goto('/register');
    await departments;
    await page.getByTestId('email').fill(email);
    await page.getByTestId('password').fill(password);
    await page.getByTestId('first-name').fill('Олена');
    await page.getByTestId('last-name').fill('Нова');
    await page.getByRole('combobox', { name: 'Кафедра' }).click();
    await page.getByRole('option', { name: 'Кафедра алгебри та інформатики' }).click();
    await page.getByTestId('register-submit').click();

    await expect(page).toHaveURL(/registration-pending/);
    await expect(page.getByTestId('pending-text')).toContainText('24 години');

    await page.getByTestId('pending-sign-in').click();
    await expect(page).toHaveURL(/localhost:8080\/login/);
    await page.fill('#username', email);
    await page.fill('#password', password);
    await page.click('button[type="submit"]');
    await expect(page).toHaveURL(/error=PENDING/);

    const link = await verificationLink(email);
    await page.goto(link);
    await page.getByTestId('verify-password').fill('not-the-registration-password');
    await page.getByTestId('verify-submit').click();
    await expect(page.getByTestId('verify-error')).toContainText('не збігається');
    await page.getByTestId('verify-password').fill(password);
    await page.getByTestId('verify-submit').click();
    await expect(page.getByTestId('verify-success')).toContainText(email);

    await page.goto(link);
    await page.getByTestId('verify-password').fill(password);
    await page.getByTestId('verify-submit').click();
    await expect(page.getByTestId('verify-invalid')).toBeVisible();

    await page.getByTestId('verify-go-pending').click();
    await expect(page).toHaveURL(/registration-pending/);
    await page.goto('/');
    await expect(page).toHaveURL(/localhost:8080\/login/);
    await page.fill('#username', email);
    await page.fill('#password', password);
    await page.click('button[type="submit"]');
    await expect(page.getByTestId('user-name')).toHaveText('Олена Нова');
    await expect(page.getByTestId('profile-roles')).toContainText('Працівник');
  });

  test('ac22 ac28 a non-institutional address is refused on the client', async ({ page }) => {
    await page.goto('/register');
    await page.getByTestId('email').fill('someone@gmail.com');
    await page.getByTestId('email').blur();
    await page.getByTestId('register-submit').click();

    await expect(page.locator('mat-error').first()).toContainText('chnu.edu.ua');
    await expect(page).toHaveURL(/register$/);
  });

  test('ac26 the second resend within a minute is refused', async ({ page }) => {
    await page.goto(`/registration-pending?email=resend.${Date.now()}@chnu.edu.ua`);
    await page.getByTestId('resend').click();
    await expect(page.getByTestId('pending-notice')).toContainText('надіслано');
    await page.getByTestId('resend').click();
    await expect(page.getByTestId('pending-notice')).toContainText('хвилину');
  });

  test('the login page links to registration', async ({ page }) => {
    await page.goto('/');
    await expect(page).toHaveURL(/localhost:8080\/login/);
    await page.click('.card__footer a');
    await expect(page).toHaveURL('http://localhost:4200/register');
  });
});
