import { readFile } from 'node:fs/promises';

import { Page, expect, test } from './fixtures';

import { kyivDay, SEED, seriousViolations, signedIn, toggleLanguage } from './helpers';

/** US-009 DoD: the unit analytics page is usable within this time on the seed. */
const USABLE_WITHIN_MS = 5000;

/** Faculty of Mathematics and Informatics and its Department of Algebra and Informatics (seed). */
const FMI = 9;
const DAI = 64;
/** A faculty outside the seed secretary's role. */
const PHILOLOGY = 12;

/** An empty chart draws only its category axis line. */
const AXIS_ONLY_PATHS = 1;

/** Long enough for the PDF, ZIP (XLSX) and UTF-8 byte order mark signatures. */
const FILE_SIGNATURE_BYTES = 4;

const FACULTY_SECTIONS = [
  'totals',
  'units',
  'trend',
  'top-performers',
  'highlights',
  'comparison',
  'by-category',
  'by-level',
  'by-source',
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
    await expect(page.getByTestId('tile-against').first()).toContainText('до ');
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
    await expect(page.getByTestId('tile-against')).toHaveText(['до 2016–2020', 'до 2016–2020']);
    await expect(page.getByTestId('unit-analytics-compared')).toHaveCount(0);

    await page.getByTestId(`comparison-row-${DAI}`).getByRole('link').click();
    await expect(page).toHaveURL(new RegExp(`/analytics/units/${DAI}\\?from=2021&to=2025$`));
    await expect(page.getByTestId('section-trend')).toBeVisible();
    await expect(page.getByTestId('section-comparison')).toHaveCount(0);
    await expect(page.getByTestId('unit-picker')).toContainText('Кафедра алгебри та інформатики');

    await page.goBack();
    await expect(page).toHaveURL(new RegExp(`/analytics/units/${FMI}\\?from=2021&to=2025$`));
    await expect(page.getByTestId('section-comparison')).toBeVisible();
  });

  test('ac2_2 lists only the departments of the faculty to its secretary and opens no other faculty', async ({
    browser,
  }) => {
    const page = await signedIn(browser, SEED.secretary);
    await page.goto('/analytics');
    await expect(page).toHaveURL(/\/analytics\/units\/\d+$/);
    await expect(page.getByTestId('section-totals')).toBeVisible();
    await expect(page.getByTestId('section-comparison')).toHaveCount(0);

    await expect(page.getByTestId(`unit-option-${DAI}`)).toBeVisible();
    await expect(page.getByTestId(`unit-option-${FMI}`)).toHaveCount(0);

    await page.goto(`/analytics/units/${FMI}`);
    await expect(page.getByTestId('section-totals')).toBeVisible();
    await page.goto(`/analytics/units/${PHILOLOGY}`);
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

    await toggleLanguage(page);
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Unit analytics');
    await expect(page.getByTestId('section-highlights')).toContainText('Highlights');
  });

  test('9.7 ac2_1 ac2_2 ac2_4 shows the trend and the comparison as spreadsheet tables with the api numbers', async ({
    browser,
  }) => {
    const page = await signedIn(browser, SEED.dean);
    const response = page.waitForResponse(
      (candidate) =>
        candidate.url().includes(`/api/v1/analytics/units/${FMI}?`) &&
        candidate.request().method() === 'GET',
    );
    await page.goto(`/analytics/units/${FMI}?from=2021&to=2025`);
    const report = (await (await response).json()) as UnitReport;

    await page
      .locator('app-pane', { has: page.getByTestId('unit-trend-multiples') })
      .getByTestId('pane-view-table')
      .click();
    const grid = page.getByTestId('unit-trend-table');
    await expect(grid.locator('thead th')).toHaveText([
      'Підрозділ',
      ...report.byYear.map((entry) => String(entry.year)),
      'Разом',
    ]);
    const yearTotals = report.byYear.map((entry) => String(entry.total));
    const sum = report.byYear.reduce((total, entry) => total + entry.total, 0);
    await expect(grid.getByTestId('year-grid-totals').locator('td')).toHaveText([
      ...yearTotals,
      String(sum),
    ]);
    await expect(page.getByTestId('year-grid-running')).toHaveCount(0);

    await page
      .locator('app-pane', { has: page.getByTestId('comparison-table') })
      .getByTestId('pane-view-table')
      .click();
    const full = page.getByTestId('comparison-full-table');
    await expect(full.locator('thead tr').first().locator('th')).toHaveText([
      'Підрозділ',
      'Нагороди',
      'Люди',
    ]);
    const total = page.getByTestId('comparison-full-total');
    await expect(total.locator('th')).toHaveText('Увесь факультет');
    await expect(total.locator('td').first()).toHaveText(String(report.totals.approved.current));
    expect(await seriousViolations(page)).toEqual([]);
  });

  test('9.7 ac2_3 pins the unit column on a phone with the newest year first and a scroll caption', async ({
    browser,
  }) => {
    const page = await signedIn(browser, SEED.dean);
    await page.setViewportSize({ width: 360, height: 800 });
    const year = new Date().getFullYear();
    await page.goto(`/analytics/units/${FMI}?from=${year - 5}&to=${year}`);
    await page
      .locator('app-pane', { has: page.getByTestId('unit-trend-multiples') })
      .getByTestId('pane-view-table')
      .click();

    const grid = page.getByTestId('unit-trend-table');
    await expect(grid.getByTestId('year-grid-year').first()).toHaveText(
      new RegExp(`^\\s*${year}\\*\\s*рік триває$`),
    );
    await expect(page.getByTestId('year-grid-running')).toHaveCount(0);
    await expect(grid.getByTestId('year-grid-year').first()).toHaveAttribute('title', 'рік триває');
    const area = page.getByTestId('table-scroll').filter({ has: grid });
    await expect(area).toHaveAttribute('tabindex', '0');
    await expect(area).toHaveAttribute('aria-label', /Нагороди за роками/);
    await expect(page.getByTestId('table-scroll-hint')).toHaveText(/\d з 6 років — гортайте →/);

    const pinned = grid.locator('tbody th').first();
    const before = await pinned.boundingBox();
    await area.evaluate((node) => node.scrollBy({ left: 200 }));
    await expect.poll(() => area.evaluate((node) => node.scrollLeft)).toBeGreaterThan(0);
    expect(Math.abs((await pinned.boundingBox())!.x - before!.x)).toBeLessThan(1);
    expect(await noHorizontalScroll(page)).toBe(true);
    expect(await seriousViolations(page)).toEqual([]);
  });

  test('9.7 ac4_1 ac4_3 keeps the rail beside the widgets and opens a performer portfolio by keyboard', async ({
    browser,
  }) => {
    const page = await signedIn(browser, SEED.dean);
    await page.setViewportSize({ width: 1280, height: 800 });
    await page.goto(`/analytics/units/${FMI}`);
    await expect(page.getByTestId('section-totals')).toBeVisible();
    await expect(page.getByTestId('section-trend').locator('.multiples__key')).toHaveCount(0);

    await page.getByTestId('section-by-source').scrollIntoViewIfNeeded();
    await expect(page.locator('app-page-header')).not.toBeInViewport();
    await expect(page.getByTestId('section-totals')).toBeInViewport();

    const link = page.getByTestId('top-performer-link').first();
    await link.focus();
    const card = page.getByTestId('top-card');
    await expect(card).toBeVisible();
    await expect(card).toContainText('Нагород за період');
    await expect(card).toContainText('Портфоліо →');
    await page.keyboard.press('Enter');
    await expect(page).toHaveURL(/\/public\/achievements\?person=\d+$/);
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

  test('ac6_1 ac6_4 puts the tiles and the departments with the counts of the api beside the main column', async ({
    browser,
  }) => {
    const page = await signedIn(browser, SEED.dean);
    const response = page.waitForResponse(
      (candidate) =>
        candidate.url().includes(`/api/v1/analytics/units/${FMI}?`) &&
        candidate.request().method() === 'GET',
    );
    await page.goto(`/analytics/units/${FMI}?from=2021&to=2025`);
    const report = (await (await response).json()) as UnitReport;
    const side = page.locator('.split__side');
    const main = page.locator('.split__main');

    await expect(side.getByTestId('tile-approved').locator('.tile__value')).toHaveText(
      String(report.totals.approved.current),
    );
    await expect(side.getByTestId(`unit-option-${FMI}`)).toContainText('Увесь факультет');
    await expect(side.getByTestId(`unit-option-${FMI}`)).toContainText(
      String(report.totals.approved.current),
    );
    const department = report.units.find((row) => row.unit.id === DAI)!;
    await expect(side.getByTestId(`unit-option-${DAI}`)).toContainText(
      String(department.approved.current),
    );
    for (const section of ['trend', 'top-performers', 'highlights']) {
      await expect(main.getByTestId(`section-${section}`)).toBeVisible();
    }

    await side.getByTestId(`unit-option-${DAI}`).click();
    await expect(page).toHaveURL(new RegExp(`/analytics/units/${DAI}\\?from=2021&to=2025$`));
    await expect(side.getByTestId(`unit-option-${DAI}`)).toHaveAttribute('aria-current', 'true');
    await expect(side.getByTestId(`unit-option-${DAI}`)).toContainText(
      String(department.approved.current),
    );
    await expect(side.getByTestId('tile-approved').locator('.tile__value')).toHaveText(
      String(department.approved.current),
    );
    expect(await seriousViolations(page)).toEqual([]);
  });

  test('9.7 ac11_3 switches the unit in place keeping the scroll and the focus', async ({
    browser,
  }) => {
    const page = await signedIn(browser, SEED.dean);
    await page.goto(`/analytics/units/${FMI}?from=2021&to=2025`);
    const side = page.locator('.split__side');
    const body = page.getByTestId('unit-analytics-body');
    await expect(page.getByTestId('section-by-source')).toBeVisible();
    let release: () => void = () => undefined;
    const held = new Promise<void>((resolve) => (release = resolve));
    await page.route(`**/api/v1/analytics/units/${DAI}?*`, async (route) => {
      await held;
      await route.continue();
    });
    const scroller = page.locator('mat-sidenav-content');
    await scroller.evaluate((element) => (element.scrollTop = 600));
    await expect.poll(() => scroller.evaluate((element) => element.scrollTop)).toBeGreaterThan(0);
    const row = side.getByTestId(`unit-option-${DAI}`);
    await row.focus();
    const before = (await row.boundingBox())!.y;

    await page.keyboard.press('Enter');
    await expect(page).toHaveURL(new RegExp(`/analytics/units/${DAI}\\?from=2021&to=2025$`));
    await expect(body).toHaveAttribute('aria-busy', 'true');
    await expect(page.getByTestId('section-trend')).toBeVisible();
    expect(Math.abs((await row.boundingBox())!.y - before)).toBeLessThan(2);
    release();

    await expect(body).toHaveAttribute('aria-busy', 'false');
    await expect(page.getByTestId('section-comparison')).toHaveCount(0);
    expect(await scroller.evaluate((element) => element.scrollTop)).toBeGreaterThan(0);
    await expect(side.getByTestId(`unit-option-${DAI}`)).toBeFocused();
  });

  test('ac6_2 period chips switch to the current year and back to the whole period', async ({
    browser,
  }) => {
    const page = await signedIn(browser, SEED.dean);
    const year = kyivDay().slice(0, 4);
    await page.goto(`/analytics/units/${FMI}?from=2021&to=2025`);
    await expect(page.getByTestId('period-chip-all')).toHaveAttribute('aria-pressed', 'false');

    await page.getByTestId('period-chip-year').click();
    await expect(page).toHaveURL(new RegExp(`/analytics/units/${FMI}\\?from=${year}&to=${year}$`));
    await expect(page.getByTestId('period-chip-year')).toHaveAttribute('aria-pressed', 'true');
    await expect(page.getByTestId('tile-against').first()).toHaveText(`до ${Number(year) - 1}`);

    await page.getByTestId('period-chip-all').click();
    await expect(page).toHaveURL(new RegExp(`/analytics/units/${FMI}$`));
    await expect(page.getByTestId('period-chip-all')).toHaveAttribute('aria-pressed', 'true');
    await expect(page.getByTestId('section-highlights')).toBeVisible();
    await expect(page.getByTestId('unit-export')).toBeEnabled();
  });
});

interface UnitReport {
  totals: { approved: { current: number } };
  byYear: { year: number; total: number }[];
  units: { unit: { id: number }; approved: { current: number } }[];
}
