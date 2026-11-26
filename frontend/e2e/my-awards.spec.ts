import { readFile } from 'node:fs/promises';

import { expect, test } from './fixtures';

import {
  FRESH_PASSWORD,
  freshEmployee,
  SEED,
  signedIn,
  signedInAs,
  toggleLanguage,
} from './helpers';

test.describe('my awards dashboard', () => {
  test('ac3_3 ac4_1 ac4_2 the home of an employee is its dashboard with progress and visibility', async ({
    browser,
  }) => {
    const page = await signedIn(browser, SEED.lecturer);
    await page.goto('/');

    await expect(page).toHaveURL(/\/dashboard$/);
    await expect(page.getByTestId('widget-KPI').getByTestId('kpi-value')).toBeVisible();
    await expect(page.getByTestId('widget-LEVEL_STAIRS').getByTestId('level-stairs')).toBeVisible();
    await expect(page.getByTestId('in-progress-pending')).toContainText(/На розгляді: \d+/);
    await expect(page.getByTestId('visibility-documents')).toContainText(/З документом: \d+/);

    await page.getByTestId('visibility-PRIVATE').click();
    await expect(page).toHaveURL(/\/awards\?status=APPROVED&visibility=PRIVATE$/);
    await expect(page.getByTestId('filter-visibility')).toContainText('Лише мені та рецензентам');
    await expect(page.getByTestId('filter-status')).toContainText('Затверджено');
  });

  test('ac4_3 exports the approved awards for a CV as text and as an English PDF', async ({
    browser,
  }) => {
    const page = await signedIn(browser, SEED.lecturer);
    await page.goto('/dashboard');

    await page.getByTestId('cv-export').click();
    const text = page.waitForEvent('download');
    await page.getByTestId('cv-export-text').click();
    const textFile = await text;
    expect(textFile.suggestedFilename()).toMatch(/^awards-cv-\d{4}-\d{2}-\d{2}\.txt$/);
    expect(await readFile(await textFile.path(), 'utf8')).toMatch(/\d{4}/);

    await toggleLanguage(page);
    await page.getByTestId('cv-export').click();
    const pdf = page.waitForEvent('download');
    await page.getByTestId('cv-export-pdf').click();
    const pdfFile = await pdf;
    expect(pdfFile.suggestedFilename()).toMatch(/^awards-cv-\d{4}-\d{2}-\d{2}\.pdf$/);
    expect((await readFile(await pdfFile.path())).subarray(0, 4).toString()).toBe('%PDF');
  });

  test('ac4_5 an employee without approved awards cannot export', async ({ browser }) => {
    const email = await freshEmployee(browser, 'cv-empty');
    const page = await signedInAs(browser, email, FRESH_PASSWORD);
    await page.goto('/dashboard');

    await expect(page.getByTestId('dashboard-empty-first')).toBeVisible();
    await expect(page.getByTestId('cv-export')).toBeDisabled();
  });
});
