import { expect, test } from './fixtures';

import {
  FRESH_PASSWORD,
  freshEmployee,
  pastDay,
  RECONNECT_MS,
  seriousViolations,
  signedInAs,
  submitWithoutDocuments,
  uniqueToken,
} from './helpers';

type Page = import('@playwright/test').Page;

const NOTICE =
  "Немає з'єднання. Введене зберігається на цьому пристрої і буде збережено як чернетку, щойно з'єднання відновиться.";
const PENDING = "Чернетку буде збережено, щойно з'єднання відновиться.";
const SUBMIT_REFUSED = "Подати нагороду можна лише з'єднавшись із сервером.";
const DOCUMENTS_PAUSED = "Документи можна додати, щойно з'єднання відновиться.";
const RECONNECTED = "З'єднання відновлено, чернетку збережено.";
const STALE = 'Нагороду змінено в іншому вікні';

function countCreations(page: Page): () => number {
  let created = 0;
  page.on('request', (request) => {
    if (request.method() === 'POST' && /\/api\/v1\/awards$/.test(request.url())) {
      created++;
    }
  });
  return () => created;
}

async function openNewForm(page: Page): Promise<void> {
  await page.goto('/awards/new');
  await expect(page.getByTestId('award-title-uk')).toBeVisible();
}

async function saveOnline(page: Page, title: string): Promise<string> {
  await openNewForm(page);
  await page.getByTestId('award-title-uk').fill(title);
  await page.getByTestId('award-save').click();
  await expect(page.getByTestId('award-form-message')).toContainText('Чернетку збережено');
  await expect(page).toHaveURL(/\/awards\/\d+\/edit$/);
  return new URL(page.url()).pathname;
}

test.describe('award draft kept offline', () => {
  test('ac2_9 a new award whose save answer was lost is created once', async ({ browser }) => {
    const email = await freshEmployee(browser, 'lost-answer');
    const title = `Грамота з утраченою відповіддю ${uniqueToken()}`;
    const phone = await signedInAs(browser, email, FRESH_PASSWORD);
    let stored = 0;
    let lose = true;
    await phone.route(/\/api\/v1\/awards$/, async (route) => {
      if (route.request().method() !== 'POST') {
        await route.fallback();
        return;
      }
      const response = await route.fetch();
      if (response.status() === 201) {
        stored++;
      }
      if (lose) {
        lose = false;
        await route.abort('failed');
        return;
      }
      await route.fulfill({ response });
    });
    await openNewForm(phone);

    await phone.getByTestId('award-title-uk').fill(title);
    await phone.getByTestId('award-save').click();
    await expect(phone.getByTestId('award-offline-notice')).toHaveText(PENDING);
    await phone.context().setOffline(true);
    await phone.context().setOffline(false);
    await expect(phone.getByTestId('award-form-message')).toHaveText(RECONNECTED, {
      timeout: RECONNECT_MS,
    });
    await phone.goto('/awards');
    await expect(phone.getByText(title)).toHaveCount(1);
    expect(stored).toBe(1);
  });

  test('ac4_2 two tabs on a new award typed offline create one draft', async ({ browser }) => {
    const email = await freshEmployee(browser, 'two-tabs');
    const token = uniqueToken();
    const first = await signedInAs(browser, email, FRESH_PASSWORD);
    const second = await first.context().newPage();
    await openNewForm(first);
    await openNewForm(second);

    await first.context().setOffline(true);
    for (const [tab, title] of [
      [first, `Перша вкладка ${token}`],
      [second, `Друга вкладка ${token}`],
    ] as const) {
      await tab.getByTestId('award-title-uk').fill(title);
      await tab.getByTestId('award-save').click();
      await expect(tab.getByTestId('award-offline-notice')).toHaveText(PENDING);
    }
    await first.context().setOffline(false);

    for (const tab of [first, second]) {
      await expect(tab.getByTestId('award-form-message')).toHaveText(RECONNECTED, {
        timeout: RECONNECT_MS,
      });
    }
    await expect(first).toHaveURL(/\/awards\/\d+\/edit$/);
    expect(new URL(second.url()).pathname).toBe(new URL(first.url()).pathname);
    const offers = await Promise.all(
      [first, second].map((tab) => tab.getByTestId('restore-offer').count()),
    );
    expect(offers.sort()).toEqual([0, 1]);
    await first.goto('/awards');
    await expect(first.getByTestId('award-item')).toHaveCount(1);
  });

  test('ac2_1 ac2_2 ac2_3 ac2_4 ac2_7 a draft entered offline is saved on reconnect and submitted elsewhere', async ({
    browser,
  }) => {
    const email = await freshEmployee(browser, 'offline-draft');
    const title = `Грамота без мережі ${uniqueToken()}`;
    const phone = await signedInAs(browser, email, FRESH_PASSWORD);
    const created = countCreations(phone);
    await openNewForm(phone);

    await phone.context().setOffline(true);
    await expect(phone.getByTestId('award-offline-notice')).toHaveText(NOTICE);
    await phone.getByTestId('award-title-uk').fill(title);
    await phone.getByTestId('award-save').click();
    await expect(phone.getByTestId('award-offline-notice')).toHaveText(PENDING);

    await phone.getByTestId('award-submit').click();
    await expect(phone.getByTestId('award-offline-submit')).toContainText(SUBMIT_REFUSED);
    await expect(phone.getByTestId('documents-hint')).toHaveText(DOCUMENTS_PAUSED);
    await expect(phone.getByTestId('documents-drop')).toHaveAttribute('aria-disabled', 'true');
    expect(await seriousViolations(phone)).toEqual([]);
    expect(created()).toBe(0);

    await phone.context().setOffline(false);
    await expect(phone.getByTestId('award-form-message')).toHaveText(RECONNECTED, {
      timeout: RECONNECT_MS,
    });
    await expect(phone).toHaveURL(/\/awards\/\d+\/edit$/);
    await expect(phone.getByTestId('award-offline-notice')).toHaveCount(0);
    await expect(phone.getByTestId('documents-hint')).not.toHaveText(DOCUMENTS_PAUSED);
    expect(created()).toBe(1);
    expect(
      await phone.evaluate(() =>
        Object.keys(localStorage).some((key) => key.endsWith('.award-new')),
      ),
    ).toBe(false);

    const desktop = await signedInAs(browser, email, FRESH_PASSWORD);
    await desktop.goto(new URL(phone.url()).pathname);
    await expect(desktop.getByTestId('award-title-uk')).toHaveValue(title);
    await desktop.getByTestId('award-category').click();
    await desktop.getByTestId('category-option-13').click();
    await desktop.getByTestId('award-organization').fill('Міністерство освіти і науки України');
    await desktop.getByTestId('award-date').fill(pastDay());
    await submitWithoutDocuments(desktop);
    await expect(desktop).toHaveURL(/\/awards\/\d+\/submitted$/);
  });

  test('ac2_5 a draft changed on another device meets the stale outcome on reconnect', async ({
    browser,
  }) => {
    const email = await freshEmployee(browser, 'offline-stale');
    const phone = await signedInAs(browser, email, FRESH_PASSWORD);
    const path = await saveOnline(phone, `Грамота ${uniqueToken()}`);

    await phone.context().setOffline(true);
    await expect(phone.getByTestId('award-offline-notice')).toHaveText(NOTICE);
    await phone.getByTestId('award-title-uk').fill('Версія телефона');

    const desktop = await signedInAs(browser, email, FRESH_PASSWORD);
    await desktop.goto(path);
    await desktop.getByTestId('award-title-uk').fill('Версія комп’ютера');
    await desktop.getByTestId('award-save').click();
    await expect(desktop.getByTestId('award-form-message')).toContainText('Чернетку збережено');

    await phone.context().setOffline(false);
    await expect(phone.getByTestId('award-form-error')).toContainText(STALE, {
      timeout: RECONNECT_MS,
    });
    await expect(phone.getByTestId('award-title-uk')).toHaveValue('Версія телефона');
  });

  test('8_1_6 a draft opened without a connection offers Try again instead of an empty form', async ({
    browser,
  }) => {
    const email = await freshEmployee(browser, 'offline-reload');
    const title = `Грамота ${uniqueToken()}`;
    const phone = await signedInAs(browser, email, FRESH_PASSWORD);
    const path = await saveOnline(phone, title);
    const draft = new RegExp(`/api/v1${path.replace(/\/edit$/, '')}$`);
    await phone.route(draft, (route) => route.abort('internetdisconnected'));

    await phone.goto(path);

    await expect(phone.getByTestId('award-load-error')).toContainText(
      'Сервер недоступний, тому чернетку не завантажено.',
    );
    await expect(phone.getByTestId('award-title-uk')).toHaveCount(0);
    await expect(phone.getByRole('heading', { level: 1 })).toHaveText('Чернетка нагороди');
    expect(await seriousViolations(phone)).toEqual([]);

    await phone.unroute(draft);
    await phone.getByTestId('award-load-retry').click();
    await expect(phone.getByTestId('award-title-uk')).toHaveValue(title);
    await expect(phone.getByTestId('award-load-error')).toHaveCount(0);
  });
});
