import { Page, expect, test } from '@playwright/test';

import {
  DEMO_PASSWORD,
  SEED,
  pastDay,
  signIn,
  signedIn,
  sql,
  submitWithoutDocuments,
  uniqueToken,
} from './helpers';

const { employee, dean } = SEED;

async function submitted(page: Page, title: string): Promise<string> {
  await page.getByTestId('nav-awards').click();
  await page.getByTestId('award-add').click();
  await page.getByTestId('award-title-uk').fill(title);
  await page.getByTestId('award-save').click();
  await expect(page).toHaveURL(/\/awards\/\d+\/edit$/);
  const id = /\/awards\/(\d+)\/edit/.exec(page.url())?.[1] ?? '';
  await page.getByTestId('award-category').click();
  await page.getByTestId('category-option-13').click();
  await page.getByTestId('award-organization').fill('Міністерство освіти і науки України');
  await page.getByTestId('award-date').fill(pastDay());
  await submitWithoutDocuments(page);
  await expect(page.getByTestId('award-submitted-name')).toContainText(title);
  return id;
}

test.describe('overdue notice', () => {
  test('ac2_4 ac2_5 a noticed request shows on the status page and in the dean filter', async ({
    page,
    browser,
  }) => {
    page.on('dialog', (dialog) => void dialog.accept());
    await signIn(page, employee, DEMO_PASSWORD);
    const title = `Грамота з нагадуванням ${uniqueToken()}`;
    const id = await submitted(page, title);
    sql(
      `update award_requests set deadline = (select min(deadline) from award_requests) - interval '1 minute', ` +
        `overdue_noticed_at = now(), overdue_noticed_level = current_level where award_id = ${id}`,
    );

    await page.goto(`/awards/${id}`);
    await expect(page.getByTestId('award-status-delay')).toContainText(
      /Термін розгляду минув \d{2}\.\d{2}\.\d{4}; керівника повідомлено \d{2}\.\d{2}\.\d{4}/,
    );

    const deanPage = await signedIn(browser, dean);
    await deanPage.goto('/reviews');
    await deanPage.getByTestId('review-filter-lower-overdue').click();
    await expect(deanPage.getByTestId('review-filter-noticed').locator('input')).toBeChecked();
    const row = deanPage.getByTestId('review-item').filter({ hasText: title });
    await expect(row.getByTestId('review-overdue')).toHaveText('Прострочено');
    await expect(row.getByTestId('review-noticed')).toContainText(
      /Керівника повідомлено \d{2}\.\d{2}\.\d{4}/,
    );
  });

  test('scrum78 the noticed row fits the review table at 1280 px', async ({ page, browser }) => {
    page.on('dialog', (dialog) => void dialog.accept());
    await signIn(page, employee, DEMO_PASSWORD);
    const title = `Грамота з нагадуванням ${uniqueToken()}`;
    const id = await submitted(page, title);
    sql(
      `update award_requests set deadline = (select min(deadline) from award_requests) - interval '1 minute', ` +
        `overdue_noticed_at = now(), overdue_noticed_level = current_level where award_id = ${id}`,
    );

    const deanPage = await signedIn(browser, dean);
    await deanPage.setViewportSize({ width: 1280, height: 900 });
    await deanPage.goto('/reviews');
    await deanPage.getByTestId('review-filter-lower-overdue').click();
    const row = deanPage.getByTestId('review-item').filter({ hasText: title });
    const noticed = row.getByTestId('review-noticed');
    await expect(noticed).toBeVisible();

    const overflow = await deanPage.evaluate(
      () => document.documentElement.scrollWidth - window.innerWidth,
    );
    expect(overflow).toBeLessThanOrEqual(0);
    const clipped = await noticed.evaluate((chip) => {
      const label = chip.querySelector('.mdc-evolution-chip__text-label') ?? chip;
      return (
        label.scrollWidth > label.clientWidth + 1 || label.scrollHeight > label.clientHeight + 1
      );
    });
    expect(clipped).toBe(false);
    const box = await deanPage.getByTestId('review-table').boundingBox();
    expect((box?.x ?? 0) + (box?.width ?? 0)).toBeLessThanOrEqual(1280);
  });
});
