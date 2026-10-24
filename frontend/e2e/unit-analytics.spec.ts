import { readFile } from 'node:fs/promises';

import { Page, expect, test } from './fixtures';

import { SEED, seriousViolations, signedIn } from './helpers';

/** US-009 DoD: the unit analytics page is usable within this time on the seed. */
const USABLE_WITHIN_MS = 5000;

/** Faculty of Mathematics and Informatics and its Department of Algebra and Informatics (seed). */
const FMI = 9;
const DAI = 64;

/** An empty chart draws only its category axis line. */
const AXIS_ONLY_PATHS = 1;

/** Long enough for the PDF, ZIP (XLSX) and UTF-8 byte order mark signatures. */
const FILE_SIGNATURE_BYTES = 4;

const FACULTY_SECTIONS = [
  'totals',
  'trend',
  'comparison',
  'by-category',
  'by-level',
  'by-source',
  'top-performers',
  'highlights',
];

async function sections(page: Page): Promise<string[]> {
  return page
    .locator('[data-testid^="section-"]')
    .evaluateAll((nodes) => nodes.map((node) => node.getAttribute('data-testid')!.slice(8)));
}

async function exported(page: Page, format: string): Promise<{ name: string; head: Buffer }> {
  const download = page.waitForEvent('download');
  await page.getByTestId('unit-export').click();
  await page.getByTestId(`unit-export-${format}`).click();
  const file = await download;
  const head = Buffer.from(await readFile(await file.path())).subarray(0, FILE_SIGNATURE_BYTES);
  return { name: file.suggestedFilename(), head };
}

async function noHorizontalScroll(page: Page): Promise<boolean> {
  return page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth);
}

test.describe('unit analytics', () => {
  test('ac2_1 ac2_3 ac2_8 opens the faculty from the navigation with every section within 5 s', async ({
    browser,
  }) => {
    const warmUp = await signedIn(browser, SEED.dean);
    await warmUp.goto(`/analytics/units/${FMI}?from=2021&to=2025`);
    await expect(warmUp.getByTestId('section-totals')).toBeVisible();
    await expect(warmUp.getByTestId('section-highlights')).toBeVisible();
    await warmUp.context().close();

    const page = await signedIn(browser, SEED.dean);
    const started = Date.now();
    await page.getByTestId('nav-unit-analytics').click();
    await expect(page.getByTestId('section-totals')).toBeVisible();
    await expect(page.getByTestId('section-highlights')).toBeVisible();
    expect(Date.now() - started).toBeLessThan(USABLE_WITHIN_MS);

    await expect(page).toHaveURL(new RegExp(`/analytics/units/${FMI}$`));
    expect(await sections(page)).toEqual(FACULTY_SECTIONS);
    await expect(page.getByTestId('unit-analytics-compared')).toContainText('у порівнянні з');
    await expect(page.getByTestId('comparison-table')).toContainText('Рівень факультету');
    const trendPaths = page.getByTestId('section-trend').locator('svg path');
    await expect.poll(() => trendPaths.count()).toBeGreaterThan(AXIS_ONLY_PATHS);
    await expect(page.getByTestId('chart-canvas').first().locator('svg')).toBeVisible();
    expect(await seriousViolations(page)).toEqual([]);
  });

  test('ac2_2 ac2_4 keeps the period in the address and opens a department with it', async ({
    browser,
  }) => {
    const page = await signedIn(browser, SEED.dean);
    await page.goto(`/analytics/units/${FMI}?from=2021&to=2025`);
    await expect(page.getByTestId('unit-analytics-compared')).toContainText(
      '2021–2025, у порівнянні з 2016–2020.',
    );
    await expect(page.getByTestId('unit-analytics-compared')).not.toContainText('не завершився');

    await page.getByTestId(`comparison-row-${DAI}`).getByRole('link').click();
    await expect(page).toHaveURL(new RegExp(`/analytics/units/${DAI}\\?from=2021&to=2025$`));
    await expect(page.getByTestId('section-trend')).toBeVisible();
    await expect(page.getByTestId('section-comparison')).toHaveCount(0);
    await expect(page.getByTestId('unit-picker')).toContainText('Кафедра алгебри та інформатики');

    await page.goBack();
    await expect(page).toHaveURL(new RegExp(`/analytics/units/${FMI}\\?from=2021&to=2025$`));
    await expect(page.getByTestId('section-comparison')).toBeVisible();
  });

  test('ac2_2 lists only the departments of the faculty to its secretary', async ({ browser }) => {
    const page = await signedIn(browser, SEED.secretary);
    await page.goto('/analytics');
    await expect(page).toHaveURL(/\/analytics\/units\/\d+$/);
    await expect(page.getByTestId('section-totals')).toBeVisible();
    await expect(page.getByTestId('section-comparison')).toHaveCount(0);

    await page.getByTestId('unit-picker').click();
    await expect(page.getByTestId(`unit-option-${DAI}`)).toBeVisible();
    await expect(page.getByTestId(`unit-option-${FMI}`)).toHaveCount(0);
    await page.keyboard.press('Escape');

    await page.goto(`/analytics/units/${FMI}`);
    await expect(page.getByTestId('unit-not-found')).toContainText('Цей підрозділ недоступний.');
  });

  test('ac2_1 refuses an account without unit analytics', async ({ browser }) => {
    const page = await signedIn(browser, SEED.employee);
    await page.goto('/');
    await expect(page.getByTestId('nav-dashboard')).toBeVisible();
    await expect(page.getByTestId('nav-unit-analytics')).toHaveCount(0);

    await page.goto('/analytics');
    await expect(page).toHaveURL(/\/forbidden$/);
  });

  test('ac2_1 ac2_7 refuses an unknown unit and a period that is not valid', async ({
    browser,
  }) => {
    const page = await signedIn(browser, SEED.dean);
    await page.goto('/analytics/units/999999');
    await expect(page.getByTestId('unit-not-found')).toBeVisible();
    await page.getByTestId('unit-not-found').getByRole('link').click();
    await expect(page).toHaveURL(new RegExp(`/analytics/units/${FMI}$`));

    await page.goto(`/analytics/units/${FMI}?from=2026&to=2021`);
    await expect(page.getByTestId('unit-analytics-invalid')).toContainText('Період некоректний.');
    await page.goto(`/analytics/units/${FMI}?from=${new Date().getFullYear() + 1}`);
    await expect(page.getByTestId('unit-analytics-invalid')).toBeVisible();
  });

  test('ac2_8 fits a phone in dark mode with only the comparison table scrolling', async ({
    browser,
  }) => {
    const page = await signedIn(browser, SEED.dean);
    await page.emulateMedia({ colorScheme: 'dark', reducedMotion: 'reduce' });
    await page.setViewportSize({ width: 360, height: 800 });
    await page.goto(`/analytics/units/${FMI}`);
    await expect(page.getByTestId('section-highlights')).toBeVisible();

    expect(await noHorizontalScroll(page)).toBe(true);
    const table = page.getByTestId('comparison-table');
    expect(
      await table.evaluate(
        (node) => node.parentElement!.scrollWidth > node.parentElement!.clientWidth,
      ),
    ).toBe(true);
    expect(await seriousViolations(page)).toEqual([]);

    await page.getByTestId('language-toggle').click();
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Unit analytics');
    await expect(page.getByTestId('section-highlights')).toContainText('Highlights');
  });

  test('ac3_6 downloads the shown period as PDF, Excel and CSV', async ({ browser }) => {
    const page = await signedIn(browser, SEED.dean);
    await page.goto(`/analytics/units/${FMI}?from=2021&to=2025`);
    await expect(page.getByTestId('section-highlights')).toBeVisible();

    const pdf = await exported(page, 'pdf');
    expect(pdf.name).toMatch(/^analytics-[\w-]+-2021-2025\.pdf$/);
    expect(pdf.head.toString('latin1')).toBe('%PDF');
    const xlsx = await exported(page, 'xlsx');
    expect(xlsx.name).toMatch(/^analytics-[\w-]+-2021-2025\.xlsx$/);
    expect(xlsx.head.subarray(0, 2).toString('latin1')).toBe('PK');
    const csv = await exported(page, 'csv');
    expect(csv.name).toMatch(/^analytics-[\w-]+-2021-2025\.csv$/);
    expect([...csv.head.subarray(0, 3)]).toEqual([0xef, 0xbb, 0xbf]);

    await page.goto(`/analytics/units/${DAI}`);
    await expect(page.getByTestId('section-trend')).toBeVisible();
    expect((await exported(page, 'csv')).name).toMatch(/^analytics-[\w-]+-\d{4}-\d{4}\.csv$/);
  });

  test('ac3_6 offers no export for an invalid period and reports a failed one', async ({
    browser,
  }) => {
    const page = await signedIn(browser, SEED.dean);
    await page.goto(`/analytics/units/${FMI}?from=2026&to=2021`);
    await expect(page.getByTestId('unit-analytics-invalid')).toBeVisible();
    await expect(page.getByTestId('unit-export')).toBeDisabled();

    await page.goto(`/analytics/units/${FMI}`);
    await expect(page.getByTestId('section-highlights')).toBeVisible();
    await page.route('**/analytics/units/*/export?**', (route) => route.abort());
    await page.getByTestId('unit-export').click();
    await page.getByTestId('unit-export-pdf').click();
    await expect(page.getByTestId('unit-export-failed')).toHaveText(
      'Не вдалося експортувати звіт.',
    );
    await expect(page.getByTestId('section-highlights')).toBeVisible();
    await expect(page.getByTestId('unit-export')).toBeEnabled();
  });
});
