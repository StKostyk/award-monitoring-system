import { readFile } from 'node:fs/promises';

import { Page, expect, test } from './fixtures';

import { SEED, seriousViolations, signedIn } from './helpers';

/** US-010 DoD: the university analytics page is usable within this time on the seed. */
const USABLE_WITHIN_MS = 10000;

/** Faculty of Mathematics and Informatics and the university itself (seed). */
const FMI = 9;
const UNIVERSITY = 1;

/** Length of the `%PDF` signature at the start of a PDF file. */
const PDF_SIGNATURE_BYTES = 4;

const UNIVERSITY_SECTIONS = [
  'compliance',
  'totals',
  'trend',
  'comparison',
  'by-category',
  'by-level',
  'by-source',
  'trends',
  'highlights',
];

async function sections(page: Page): Promise<string[]> {
  return page
    .locator('[data-testid^="section-"]')
    .evaluateAll((nodes) => nodes.map((node) => node.getAttribute('data-testid')!.slice(8)));
}

async function noHorizontalScroll(page: Page): Promise<boolean> {
  return page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth);
}

test.describe('university analytics', () => {
  test('ac2_1 ac2_3 ac2_9 opens the university from the navigation with every section within 10 s', async ({
    browser,
  }) => {
    const warmUp = await signedIn(browser, SEED.rector);
    await warmUp.goto('/analytics/university');
    await expect(warmUp.getByTestId('section-highlights')).toBeVisible();
    await warmUp.context().close();

    const page = await signedIn(browser, SEED.rector);
    await expect(page.getByTestId('nav-unit-analytics')).toBeVisible();
    const started = Date.now();
    await page.getByTestId('nav-university-analytics').click();
    await expect(page.getByTestId('compliance-status')).toBeVisible();
    await expect(page.getByTestId('section-highlights')).toBeVisible();
    expect(Date.now() - started).toBeLessThan(USABLE_WITHIN_MS);

    await expect(page).toHaveURL(/\/analytics\/university$/);
    await expect(page.getByTestId('nav-university-analytics')).toHaveAttribute(
      'aria-current',
      'page',
    );
    await expect(page.getByTestId('nav-unit-analytics')).not.toHaveAttribute(
      'aria-current',
      'page',
    );
    expect(await sections(page)).toEqual(UNIVERSITY_SECTIONS);
    await expect(page.getByTestId('university-analytics-compared')).toContainText('у порівнянні з');
    await expect(page.getByTestId('university-projection')).toContainText(
      /Прогноз на \d{4}|Замало/,
    );
    await expect(page.getByTestId(`comparison-row-${UNIVERSITY}`)).toContainText(
      'Рівень університету',
    );
    expect(await seriousViolations(page)).toEqual([]);
  });

  test('ac2_2 ac2_4 keeps the period in the address and opens a faculty with it', async ({
    browser,
  }) => {
    const page = await signedIn(browser, SEED.rectorSecretary);
    await page.goto('/analytics/university?from=2021&to=2025');
    await expect(page.getByTestId('university-analytics-compared')).toContainText(
      '2021–2025, у порівнянні з 2016–2020.',
    );
    await expect(page.getByTestId('university-analytics-compared')).not.toContainText(
      'не завершився',
    );
    const colleges = page.locator('[data-testid^="comparison-row-"]').filter({ hasText: 'коледж' });
    if ((await colleges.count()) > 0) {
      await expect(colleges.first().getByRole('link')).toHaveCount(0);
    }

    await page.getByTestId(`comparison-row-${FMI}`).getByRole('link').click();
    await expect(page).toHaveURL(new RegExp(`/analytics/units/${FMI}\\?from=2021&to=2025$`));
    await expect(page.getByTestId('section-comparison')).toBeVisible();

    await page.goBack();
    await expect(page).toHaveURL(/\/analytics\/university\?from=2021&to=2025$/);
    await expect(page.getByTestId('section-trends')).toBeVisible();
  });

  test('ac2_7 refreshes only the compliance panel and shows its own failure', async ({
    browser,
  }) => {
    const page = await signedIn(browser, SEED.rector);
    await page.goto('/analytics/university');
    await expect(page.getByTestId('compliance-as-of')).toBeVisible();
    await expect(page.getByTestId('section-highlights')).toBeVisible();

    const requests: string[] = [];
    page.on('request', (request) => {
      if (request.url().includes('/analytics/university')) {
        requests.push(new URL(request.url()).pathname);
      }
    });
    const reloaded = page.waitForResponse('**/analytics/university/compliance');
    await page.getByTestId('compliance-refresh').click();
    await reloaded;
    await expect(page.getByTestId('compliance-refresh')).toBeEnabled();
    expect(requests.every((path) => path.endsWith('/compliance'))).toBe(true);
    expect(requests).toHaveLength(1);

    await page.route('**/analytics/university/compliance', (route) => route.abort());
    await page.getByTestId('compliance-refresh').click();
    await expect(page.getByTestId('compliance-error')).toContainText(
      'Не вдалося завантажити зведення відповідності.',
    );
    await expect(page.getByTestId('section-highlights')).toBeVisible();
  });

  test('ac2_1 refuses every account without university analytics', async ({ browser }) => {
    for (const account of [SEED.dean, SEED.employee, SEED.gdpr]) {
      const page = await signedIn(browser, account);
      await page.goto('/');
      await expect(page.getByTestId('nav-university-analytics')).toHaveCount(0);
      await page.goto('/analytics/university');
      await expect(page).toHaveURL(/\/forbidden$/);
      await page.context().close();
    }
  });

  test('ac2_8 refuses a period that is not valid and keeps the compliance panel', async ({
    browser,
  }) => {
    const page = await signedIn(browser, SEED.rector);
    await page.goto('/analytics/university?from=2026&to=2021');
    await expect(page.getByTestId('university-analytics-invalid')).toContainText(
      'Період некоректний.',
    );
    await expect(page.getByTestId('compliance-status')).toBeVisible();
    await expect(page.getByTestId('section-totals')).toHaveCount(0);
  });

  test('ac2_9 fits a phone in dark mode with only the comparison table scrolling', async ({
    browser,
  }) => {
    const page = await signedIn(browser, SEED.rector);
    await page.emulateMedia({ colorScheme: 'dark', reducedMotion: 'reduce' });
    await page.setViewportSize({ width: 360, height: 800 });
    await page.goto('/analytics/university');
    await expect(page.getByTestId('section-highlights')).toBeVisible();

    expect(await noHorizontalScroll(page)).toBe(true);
    expect(await seriousViolations(page)).toEqual([]);

    await page.getByTestId('language-toggle').click();
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('University analytics');
    await expect(page.getByTestId('section-trends')).toContainText('Rising');
    expect(await noHorizontalScroll(page)).toBe(true);
  });

  test('ac3_5 downloads the board report of the shown period', async ({ browser }) => {
    const page = await signedIn(browser, SEED.rector);
    await page.goto('/analytics/university?from=2021&to=2025');
    await expect(page.getByTestId('section-highlights')).toBeVisible();

    const download = page.waitForEvent('download');
    await page.getByTestId('board-report').click();
    const file = await download;
    const head = Buffer.from(await readFile(await file.path())).subarray(0, PDF_SIGNATURE_BYTES);

    expect(file.suggestedFilename()).toMatch(/^board-report-[\w-]+-2021-2025\.pdf$/);
    expect(head.toString('latin1')).toBe('%PDF');
  });

  test('ac3_5 offers no board report for an invalid period and reports a failed one', async ({
    browser,
  }) => {
    const page = await signedIn(browser, SEED.rectorSecretary);
    await page.goto('/analytics/university?from=2026&to=2021');
    await expect(page.getByTestId('university-analytics-invalid')).toBeVisible();
    await expect(page.getByTestId('board-report')).toBeDisabled();

    await page.goto('/analytics/university');
    await expect(page.getByTestId('section-highlights')).toBeVisible();
    await page.route('**/analytics/university/report?**', (route) => route.abort());
    await page.getByTestId('board-report').click();
    await expect(page.getByTestId('board-report-failed')).toHaveText(
      'Не вдалося експортувати звіт.',
    );
    await expect(page.getByTestId('section-highlights')).toBeVisible();
    await expect(page.getByTestId('board-report')).toBeEnabled();
  });
});
