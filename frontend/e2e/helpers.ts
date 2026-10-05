import { execFileSync } from 'node:child_process';

import AxeBuilder from '@axe-core/playwright';
import type { Browser, Page } from '@playwright/test';
import { expect } from '@playwright/test';

const mailpit = 'http://localhost:8025';
const demoPassword = 'Passw0rd-demo';

interface MessageSummary {
  ID: string;
  Subject: string;
  To: { Address: string }[];
}

async function messagesTo(email: string, subject = ''): Promise<MessageSummary[]> {
  const list = await (await fetch(`${mailpit}/api/v1/messages?limit=100`)).json();
  return ((list.messages ?? []) as MessageSummary[]).filter(
    (m) =>
      m.To?.some((to) => to.Address.toLowerCase() === email.toLowerCase()) &&
      m.Subject.includes(subject),
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
export async function registerAndVerify(
  page: Page,
  email: string,
  password: string,
): Promise<void> {
  await fetch('http://localhost:8080/api/v1/auth/register', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      email,
      password,
      firstName: 'Ірина',
      lastName: 'Нова',
      organizationId: 64,
    }),
  });
  await page.goto(await linkFor(email, 'verify-email'));
  await page.getByTestId('verify-password').fill(password);
  await page.getByTestId('verify-submit').click();
  await expect(page.getByTestId('verify-success')).toContainText(email);
}

/** Submits the award form and accepts the notice that the award has no documents. */
export async function submitWithoutDocuments(page: Page): Promise<void> {
  await page.getByTestId('award-submit').click();
  await expect(page.getByRole('dialog')).toContainText('Ви не додали жодного документа');
  await page.getByTestId('confirm-accept').click();
}

/** Submits the login form of the authorization server starting from the app root. */
export async function signIn(page: Page, email: string, password: string): Promise<void> {
  await page.goto('/');
  await expect(page).toHaveURL(/localhost:8080\/login/);
  await page.fill('#username', email);
  await page.fill('#password', password);
  await page.click('button[type="submit"]');
}

/** Signs a seed account in with the demo password and waits for the navigation. */
export async function signInAsSeed(page: Page, email: string): Promise<void> {
  await signIn(page, email, demoPassword);
  await expect(page.getByTestId('nav-awards')).toBeVisible();
}

/** A page in a new browser context with a seed account signed in; native dialogs are accepted. */
export async function signedIn(browser: Browser, email: string): Promise<Page> {
  const page = await (await browser.newContext()).newPage();
  page.on('dialog', (dialog) => void dialog.accept());
  await signInAsSeed(page, email);
  return page;
}

/** The computed background colour of the first element matching the selector. */
export function background(page: Page, selector: string): Promise<string> {
  return page
    .locator(selector)
    .first()
    .evaluate((element) => getComputedStyle(element).backgroundColor);
}

/** The serious and critical WCAG 2.1 AA violations of the page, with the elements they concern. */
export async function seriousViolations(page: Page): Promise<string[]> {
  const result = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21aa'])
    .analyze();
  return result.violations
    .filter((violation) => violation.impact === 'serious' || violation.impact === 'critical')
    .map(
      (violation) => `${violation.id}: ${violation.nodes.map((node) => node.target).join(' | ')}`,
    );
}

/**
 * Runs one statement on the development database inside the `award-postgres` container and answers its output
 * without headers; for fixtures the user interface cannot create, such as reviewer decisions before Epic 4.
 */
export function sql(statement: string): string {
  return execFileSync(
    'docker',
    [
      'exec',
      '-i',
      'award-postgres',
      'psql',
      '-U',
      'postgres',
      '-d',
      'award_monitoring',
      '-tAc',
      statement,
    ],
    { encoding: 'utf-8' },
  ).trim();
}

/** Twelve random lower-case letters, to tell the records of one test run apart. */
export function uniqueToken(): string {
  return Array.from(
    { length: 12 },
    () => 'abcdefghijklmnopqrstuvwxyz'[Math.floor(Math.random() * 26)],
  ).join('');
}

/** The Kyiv calendar day the given number of days from today, as `YYYY-MM-DD`. */
export function kyivDay(offset = 0): string {
  const [year, month, day] = new Intl.DateTimeFormat('en-CA', { timeZone: 'Europe/Kyiv' })
    .format(new Date())
    .split('-')
    .map(Number);
  return new Date(Date.UTC(year, month - 1, day + offset)).toISOString().substring(0, 10);
}

/** The Kyiv date a review period of the given working days starting now ends on (weekends skipped). */
export function workingDaysAhead(days: number): string {
  let offset = 0;
  const weekday = (shift: number): number => new Date(`${kyivDay(shift)}T00:00:00Z`).getUTCDay();
  while (weekday(offset) === 0 || weekday(offset) === 6) {
    offset++;
  }
  for (let left = days; left > 0;) {
    offset++;
    if (weekday(offset) !== 0 && weekday(offset) !== 6) {
      left--;
    }
  }
  return kyivDay(offset);
}

/** A random award date more than a year back, so that duplicate checks of parallel tests do not meet. */
export function pastDay(): string {
  return kyivDay(-400 - Math.floor(Math.random() * 10000));
}

/** A `YYYY-MM-DD` day as the Ukrainian interface shows it. */
export function shownDay(isoDay: string): string {
  const [year, month, day] = isoDay.split('-');
  return `${day}.${month}.${year}`;
}
