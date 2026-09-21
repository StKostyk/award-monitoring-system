import type { Page } from '@playwright/test';
import { expect } from '@playwright/test';

const mailpit = 'http://localhost:8025';

interface MessageSummary {
  ID: string;
  Subject: string;
  To: { Address: string }[];
}

async function messagesTo(email: string, subject = ''): Promise<MessageSummary[]> {
  const list = await (await fetch(`${mailpit}/api/v1/messages?limit=100`)).json();
  return ((list.messages ?? []) as MessageSummary[]).filter(
    (m) => m.To?.some((to) => to.Address.toLowerCase() === email.toLowerCase()) && m.Subject.includes(subject),
  );
}

/** The newest link `http://localhost:4200/<path>?token=...` sent to the address, waiting up to 20 seconds. */
export async function linkFor(email: string, path: string, minimumMessages = 1): Promise<string> {
  const pattern = new RegExp(`http://localhost:4200/${path}\\?token=[A-Za-z0-9_-]+`);
  for (let attempt = 0; attempt < 40; attempt++) {
    const candidates: string[] = [];
    for (const message of await messagesTo(email)) {
      const full = await (await fetch(`${mailpit}/api/v1/message/${message.ID}`)).json();
      const match = pattern.exec(full.Text ?? '');
      if (match) {
        candidates.push(match[0]);
      }
    }
    if (candidates.length >= minimumMessages) {
      return candidates[0];
    }
    await new Promise((resolve) => setTimeout(resolve, 500));
  }
  throw new Error(`No ${path} email for ${email}`);
}

/** Number of messages to the address whose subject contains the text. */
export async function countMessages(email: string, subject: string): Promise<number> {
  return (await messagesTo(email, subject)).length;
}

/** Registers and verifies a fresh account through the API and the verification page. */
export async function registerAndVerify(page: Page, email: string, password: string): Promise<void> {
  await fetch('http://localhost:8080/api/v1/auth/register', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password, firstName: 'Ірина', lastName: 'Нова', organizationId: 64 }),
  });
  await page.goto(await linkFor(email, 'verify-email'));
  await page.getByTestId('verify-password').fill(password);
  await page.getByTestId('verify-submit').click();
  await expect(page.getByTestId('verify-success')).toContainText(email);
}

/** Submits the login form of the authorization server starting from the app root. */
export async function signIn(page: Page, email: string, password: string): Promise<void> {
  await page.goto('/');
  await expect(page).toHaveURL(/localhost:8080\/login/);
  await page.fill('#username', email);
  await page.fill('#password', password);
  await page.click('button[type="submit"]');
}
