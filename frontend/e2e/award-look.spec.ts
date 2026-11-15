import { Page, expect, test } from './fixtures';

import {
  DEMO_PASSWORD,
  FRESH_PASSWORD,
  SEED,
  freshEmployee,
  seriousViolations,
  settled,
  signIn,
  uniqueToken,
} from './helpers';

function pdf(): Buffer {
  const content = Buffer.alloc(2048, 0x20);
  content.write(`%PDF-1.4\n% ${uniqueToken()}\n`);
  return content;
}

/** A page of the list itself, not one of the one-item requests that count the statuses. */
function listPage(url: URL): boolean {
  return url.pathname.endsWith('/api/v1/awards') && url.searchParams.get('size') !== '1';
}

async function noSidewaysScroll(page: Page): Promise<void> {
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
}

test.describe('award list, award page and new award in the new look', () => {
  test('ac5_1 rows show level, title, recipient, date and status; chips count and filter', async ({
    page,
  }) => {
    await signIn(page, SEED.employee, DEMO_PASSWORD);
    await page.goto('/awards');
    const item = page.getByTestId('award-item').first();
    await expect(item.getByTestId('award-title')).not.toBeEmpty();
    await expect(item.getByTestId('award-status')).not.toBeEmpty();
    await expect(page.getByTestId('award-level').first()).not.toBeEmpty();
    await expect(page.getByTestId('award-recipient').first()).not.toBeEmpty();

    const labels = { DRAFT: 'Чернетка', PENDING: 'На розгляді', APPROVED: 'Затверджено' };
    await expect(page.getByTestId('status-chip-all').getByTestId('status-count')).toHaveText(
      /^\d+$/,
    );
    let chosen: { status: keyof typeof labels; count: number } | null = null;
    for (const status of Object.keys(labels) as (keyof typeof labels)[]) {
      const count = Number(
        await page.getByTestId(`status-chip-${status}`).getByTestId('status-count').textContent(),
      );
      if (count > 0) {
        chosen = { status, count };
        break;
      }
    }
    if (chosen === null) {
      throw new Error('The seed employee has no awards to filter');
    }
    const { status, count } = chosen;
    const chip = page.getByTestId(`status-chip-${status}`);
    await chip.click();
    await expect(chip).toHaveAttribute('aria-pressed', 'true');
    await expect(page.getByTestId('awards-loading')).toHaveCount(0);
    const statuses = page.getByTestId('award-status');
    await expect(statuses.first()).toContainText(labels[status]);
    expect(await statuses.count()).toBe(Math.min(count, 20));
    expect(await seriousViolations(page)).toEqual([]);

    await page.setViewportSize({ width: 360, height: 760 });
    await expect(page.getByTestId('status-chips')).toBeVisible();
    await expect(page.getByTestId('filter-status')).toBeHidden();
    await noSidewaysScroll(page);
  });

  test('ac5_4 ac5_5 an empty list and a search without results show an empty state', async ({
    browser,
    page,
  }) => {
    const email = await freshEmployee(browser, 'empty-list');
    await signIn(page, email, FRESH_PASSWORD);
    let release: () => void = () => undefined;
    const held = new Promise<void>((resolve) => (release = resolve));
    await page.route(listPage, async (route) => {
      await held;
      await route.continue();
    });
    await page.goto('/awards');
    await expect(page.getByTestId('awards-loading')).toBeVisible();
    await expect(page.getByTestId('skeleton-row').first()).toBeVisible();
    await expect(page.getByTestId('award-list')).toHaveCount(0);
    release();

    const empty = page.getByTestId('awards-empty');
    await expect(empty).toContainText('Нагород ще немає');
    await expect(page.getByTestId('status-chip-all').getByTestId('status-count')).toHaveText('0');
    await expect(empty.getByTestId('awards-empty-add')).toHaveAttribute('href', '/awards/new');

    await page.getByTestId('status-chip-REJECTED').click();
    await expect(empty).toContainText('Немає нагород, що відповідають пошуку.');
    await empty.getByTestId('awards-reset').click();
    await expect(page.getByTestId('status-chip-all')).toHaveAttribute('aria-pressed', 'true');
    await expect(empty).toContainText('Нагород ще немає');
    await settled(page);
    expect(await seriousViolations(page)).toEqual([]);
  });

  test('ac5_3 a dropped document starts the draft and another file replaces it', async ({
    browser,
    page,
  }) => {
    const email = await freshEmployee(browser, 'upload-step');
    await signIn(page, email, FRESH_PASSWORD);
    page.on('dialog', (dialog) => void dialog.accept());
    await page.goto('/awards/new');
    await expect(page.getByTestId('upload-step')).toContainText('Документ додасться до нагороди');
    expect(await seriousViolations(page)).toEqual([]);

    await page
      .getByTestId('upload-input')
      .setInputFiles({ name: 'Грамота_МОН.pdf', mimeType: 'application/pdf', buffer: pdf() });
    await expect(page).toHaveURL(/\/awards\/\d+\/edit$/);
    await expect(page.getByTestId('award-title-uk')).toHaveValue('Грамота МОН');
    const list = page.getByTestId('documents-list');
    await expect(list).toContainText('Грамота_МОН.pdf');

    await page
      .getByTestId('document-replace-input')
      .setInputFiles({ name: 'scan.pdf', mimeType: 'application/pdf', buffer: pdf() });
    await expect(list).toContainText('scan.pdf');
    await expect(list).not.toContainText('Грамота_МОН.pdf');
    await expect(list.locator('li')).toHaveCount(1);
    await expect(list.getByTestId('document-replace')).toBeVisible();
  });

  test('ac5_3 edge a file outside the rules is refused and skipping opens the form', async ({
    page,
  }) => {
    await signIn(page, SEED.employee, DEMO_PASSWORD);
    let created = 0;
    page.on('request', (request) => {
      if (request.method() === 'POST' && /\/api\/v1\/awards$/.test(request.url())) {
        created++;
      }
    });
    await page.goto('/awards/new');
    await page
      .getByTestId('upload-input')
      .setInputFiles({ name: 'notes.txt', mimeType: 'text/plain', buffer: Buffer.from('x') });
    await expect(page.getByTestId('upload-problem')).toContainText('Непідтримуваний формат');

    await page.getByTestId('upload-skip').click();
    await expect(page.getByTestId('award-title-uk')).toBeVisible();
    await expect(page).toHaveURL(/\/awards\/new$/);
    expect(created).toBe(0);
  });

  test('edge a scanner outage lets the person continue without a document', async ({
    browser,
    page,
  }) => {
    const email = await freshEmployee(browser, 'scanner-down');
    await signIn(page, email, FRESH_PASSWORD);
    await page.route(/\/api\/v1\/awards\/\d+\/documents$/, (route) =>
      route.fulfill({
        status: 503,
        contentType: 'application/problem+json',
        body: JSON.stringify({ type: 'urn:awards:problem:scanner-unavailable', status: 503 }),
      }),
    );
    await page.goto('/awards/new');
    await page
      .getByTestId('upload-input')
      .setInputFiles({ name: 'diploma.pdf', mimeType: 'application/pdf', buffer: pdf() });
    await expect(page.getByTestId('upload-problem')).toContainText('Перевірка файлів недоступна');

    await page.getByTestId('upload-continue').click();
    await expect(page).toHaveURL(/\/awards\/\d+\/edit$/);
    await expect(page.getByTestId('award-title-uk')).toHaveValue('diploma');
  });
});
