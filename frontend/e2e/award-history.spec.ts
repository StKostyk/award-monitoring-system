import { Browser, Page, expect, test } from '@playwright/test';

import { pastDay, signIn, uniqueToken } from './helpers';

const demo = 'Passw0rd-demo';
const employee = 'employee.fmi@chnu.edu.ua';
const dean = 'dean.fmi@chnu.edu.ua';
const admin = 'admin@chnu.edu.ua';

async function signedIn(browser: Browser, email: string): Promise<Page> {
  const page = await (await browser.newContext()).newPage();
  page.on('dialog', (dialog) => void dialog.accept());
  await signIn(page, email, demo);
  await expect(page.getByTestId('nav-awards')).toBeVisible();
  return page;
}

async function open(page: Page, id: string): Promise<void> {
  const versions = page.waitForResponse((response) =>
    response.url().includes(`/awards/${id}/versions`),
  );
  await page.goto(`/awards/${id}`);
  await versions;
}

async function actions(page: Page): Promise<string[]> {
  return (await page.getByTestId('award-history-action').allTextContents()).map((text) =>
    text.trim(),
  );
}

test.describe('award history and audit log', () => {
  test('ac2_1 to ac2_8 the owner, a dean and an auditor each see their part', async ({
    browser,
  }) => {
    const first = `Лист ${uniqueToken()}`;
    const second = `Грамота МОН ${uniqueToken()}`;
    const owner = await signedIn(browser, employee);
    await owner.getByTestId('nav-awards').click();
    await owner.getByTestId('award-add').click();
    await owner.getByTestId('award-title-uk').fill(first);
    await owner.getByTestId('award-save').click();
    await expect(owner.getByTestId('award-form-message')).toContainText('Чернетку збережено');
    await expect(owner).toHaveURL(/\/awards\/\d+\/edit$/);
    await owner.getByTestId('award-title-uk').fill(second);
    const saved = owner.waitForResponse((response) => response.request().method() === 'PUT');
    await owner.getByTestId('award-save').click();
    await saved;
    await expect(owner.getByTestId('award-form-message')).toContainText('Чернетку збережено');
    const id = /\/awards\/(\d+)\/edit/.exec(owner.url())?.[1] ?? '';
    await owner.getByTestId('award-category').click();
    await owner.getByTestId('category-option-13').click();
    await owner.getByTestId('award-organization').fill('Міністерство освіти і науки України');
    await owner.getByTestId('award-date').fill(pastDay());
    await owner.getByTestId('award-submit').click();
    await expect(owner.getByTestId('award-submitted-name')).toContainText(second);

    await open(owner, id);
    expect(await actions(owner)).toEqual(['Подано', 'Змінено', 'Змінено', 'Створено']);
    await expect(owner.getByTestId('award-history-meta').first()).toContainText(
      /\d{2}\.\d{2}\.\d{4}, \d{2}:\d{2}/,
    );
    await expect(owner.getByTestId('award-history-change')).toContainText([
      `Назва українською: ${first} → ${second}`,
    ]);
    await expect(
      owner.getByTestId('award-history-change').filter({ hasText: 'Категорія' }),
    ).toContainText('— →');
    await expect(owner.getByTestId('award-audit-tab')).toHaveCount(0);
    await owner.getByTestId('award-history-view').last().click();
    await expect(owner.getByTestId('version-field-titleUk')).toContainText(first);
    await expect(owner.getByTestId('version-field-categoryId')).toHaveText('—');
    await owner.getByTestId('version-dialog-close').click();
    await owner.getByTestId('language-toggle').click();
    await expect(owner.getByRole('heading', { name: 'Change history' })).toBeVisible();
    expect(await actions(owner)).toEqual(['Submitted', 'Changed', 'Changed', 'Created']);

    const reader = await signedIn(browser, dean);
    await open(reader, id);
    expect(await actions(reader)).toEqual(['Подано']);
    await expect(reader.getByTestId('award-audit-tab')).toHaveCount(0);

    const auditor = await signedIn(browser, admin);
    await open(auditor, id);
    const trail = auditor.waitForResponse((response) =>
      response.url().includes(`/awards/${id}/audit-trail`),
    );
    await auditor.getByTestId('award-audit-tab').click();
    await trail;
    await expect(auditor.getByTestId('audit-action').first()).toBeVisible();
    await expect(auditor.getByTestId('audit-action')).toContainText(['AWARD_SUBMITTED']);
    await auditor.getByTestId('audit-row').filter({ hasText: 'AWARD_SUBMITTED' }).click();
    await expect(auditor.getByTestId('audit-new').first()).toBeVisible();
    const download = auditor.waitForEvent('download');
    await auditor.getByTestId('audit-export').click();
    expect((await download).suggestedFilename()).toMatch(
      new RegExp(`^award-${id}-audit-\\d{4}-\\d{2}-\\d{2}\\.csv$`),
    );
    await Promise.all([owner, reader, auditor].map((page) => page.context().close()));
  });
});
