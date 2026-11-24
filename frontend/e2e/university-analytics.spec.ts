import { readFile } from 'node:fs/promises';

import { Page, expect, test } from './fixtures';

import { keepPreferencesLocal, SEED, seriousViolations, signedIn, toggleLanguage } from './helpers';

/** US-010 DoD: the university analytics page is usable within this time on the seed. */
const USABLE_WITHIN_MS = 10000;

/** Faculty of Mathematics and Informatics and the university itself (seed). */
const FMI = 9;
const UNIVERSITY = 1;

/** Length of the `%PDF` signature at the start of a PDF file. */
const PDF_SIGNATURE_BYTES = 4;

const UNIVERSITY_SECTIONS = [
  'totals',
  'faculties',
  'highlights',
  'trend',
  'by-category',
  'by-level',
  'by-source',
  'comparison',
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
    await expect(page.getByTestId('section-highlights')).toBeVisible();
    await expect(page.getByTestId('compliance-status')).toHaveCount(0);
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
    await expect(page.getByTestId('section-trend')).toBeVisible();
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

  test('ac2_8 refuses a period that is not valid', async ({ browser }) => {
    const page = await signedIn(browser, SEED.rector);
    await page.goto('/analytics/university?from=2026&to=2021');
    await expect(page.getByTestId('university-analytics-invalid')).toContainText(
      'Період некоректний.',
    );
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

    await toggleLanguage(page);
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('University analytics');
    await expect(page.getByTestId('university-projection')).toContainText(
      /Projected for|Too few complete years/,
    );
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

  test('9.3 ac2_4 redraws the charts in the colours of the new scheme without a reload', async ({
    browser,
  }) => {
    const page = await signedIn(browser, SEED.rector);
    await keepPreferencesLocal(page);
    await page.emulateMedia({ colorScheme: 'light', reducedMotion: 'reduce' });
    await page.goto('/analytics/university');
    const chart = page.getByTestId('chart-canvas').first().locator('svg');
    await expect(chart).toBeVisible();
    await page.evaluate(() => Object.assign(window, { sameDocument: true }));

    const light = await chartColours(page);
    await page.getByTestId('color-scheme-menu').click();
    await page.getByTestId('color-scheme-dark').click();

    await expect.poll(() => chartColours(page)).not.toEqual(light);
    expect(await page.evaluate(() => 'sameDocument' in window)).toBe(true);
    expect(
      await chart
        .locator('text')
        .first()
        .evaluate((node) => getComputedStyle(node).fontFamily),
    ).toContain('Onest');
  });

  test('ac6_3 ac6_4 lays the bento out in four, two and one columns with the numbers of the api', async ({
    browser,
  }) => {
    const page = await signedIn(browser, SEED.rector);
    const response = page.waitForResponse(
      (candidate) =>
        candidate.url().includes('/api/v1/analytics/university?') &&
        candidate.request().method() === 'GET',
    );
    await page.goto('/analytics/university?from=2021&to=2025');
    const report = (await (await response).json()) as UniversityReport;

    await expect(page.getByTestId('tile-approved').locator('.tile__value')).toHaveText(
      String(report.totals.approved.current),
    );
    for (const row of report.units.filter((candidate) => !candidate.facultyLevel)) {
      await expect(page.getByTestId(`faculty-bar-${row.unit.id}`)).toContainText(
        String(row.approved.current),
      );
    }
    await expect(page.getByTestId('period-chip-all')).toHaveAttribute('aria-pressed', 'false');
    await expect(page.getByTestId('board-report')).toBeEnabled();

    expect(await bentoColumns(page)).toBe(4);
    expect(await tileSpan(page, 'faculties')).toEqual(['span 3', 'span 2']);
    expect(await tileSpan(page, 'trend')).toEqual(['span 4', 'auto']);
    await page.setViewportSize({ width: 900, height: 900 });
    await expect.poll(() => bentoColumns(page)).toBe(2);
    await page.setViewportSize({ width: 360, height: 800 });
    await expect.poll(() => bentoColumns(page)).toBe(1);
    expect(await noHorizontalScroll(page)).toBe(true);

    await page.getByTestId(`faculty-bar-${FMI}`).getByRole('link').click();
    await expect(page).toHaveURL(new RegExp(`/analytics/units/${FMI}\\?from=2021&to=2025$`));
  });
});

interface UniversityReport {
  totals: { approved: { current: number } };
  units: { unit: { id: number }; facultyLevel: boolean; approved: { current: number } }[];
}

function bentoColumns(page: Page): Promise<number> {
  return page
    .locator('app-bento')
    .evaluate((node) => getComputedStyle(node).gridTemplateColumns.split(' ').length);
}

function tileSpan(page: Page, section: string): Promise<string[]> {
  return page.getByTestId(`section-${section}`).evaluate((node) => {
    const tile = getComputedStyle(node.closest('app-bento-tile')!);
    return [tile.gridColumnStart, tile.gridRowStart];
  });
}

async function chartColours(page: Page): Promise<string[]> {
  return page
    .getByTestId('chart-canvas')
    .first()
    .locator('svg path[fill], svg text[fill]')
    .evaluateAll((nodes) => [...new Set(nodes.map((node) => node.getAttribute('fill')!))].sort());
}
