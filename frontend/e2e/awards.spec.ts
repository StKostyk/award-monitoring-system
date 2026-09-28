import { expect, test } from '@playwright/test';

import { signIn } from './helpers';

const demo = 'Passw0rd-demo';
const employee = 'employee.fmi@chnu.edu.ua';

type Page = import('@playwright/test').Page;

function token(): string {
  return Array.from({ length: 12 }, () => 'abcdefghijklmnopqrstuvwxyz'[Math.floor(Math.random() * 26)]).join('');
}

function pastDay(): string {
  const date = new Date();
  date.setDate(date.getDate() - 400 - Math.floor(Math.random() * 10000));
  return date.toISOString().substring(0, 10);
}

function daysAgo(days: number): string {
  const date = new Date();
  date.setDate(date.getDate() - days);
  return date.toISOString().substring(0, 10);
}

async function fillComplete(page: Page, title: string, date: string): Promise<void> {
  await page.getByTestId('award-title-uk').fill(title);
  await chooseCategory(page, 13);
  await page.getByTestId('award-organization').fill('Міністерство освіти і науки України');
  await page.getByTestId('award-date').fill(date);
}

async function openAwards(page: Page): Promise<void> {
  const loaded = page.waitForResponse(
    (response) => response.request().method() === 'GET' && /\/api\/v1\/awards\?/.test(response.url()),
  );
  await page.getByTestId('nav-awards').click();
  await loaded;
}

async function chooseCategory(page: Page, id: number): Promise<void> {
  await page.getByTestId('award-category').click();
  await page.getByTestId(`category-option-${id}`).click();
}

test.describe('award drafts and submission on a phone', () => {
  test.use({ viewport: { width: 360, height: 740 } });

  test.beforeEach(async ({ page }) => {
    page.on('dialog', (dialog) => void dialog.accept());
    await signIn(page, employee, demo);
    await expect(page.getByTestId('nav-awards')).toBeVisible();
  });

  test('ac1_1 ac1_5 ac1_9 a draft is saved, completed and submitted', async ({ page }) => {
    const title = `Грамота МОН ${token()}`;
    await openAwards(page);
    await page.getByTestId('award-add').click();
    await expect(page).toHaveURL(/\/awards\/new$/);
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(360);

    await page.getByTestId('award-title-uk').fill(title);
    await page.getByTestId('award-save').click();
    await expect(page.getByTestId('award-form-message')).toContainText('Чернетку збережено');
    await expect(page).toHaveURL(/\/awards\/\d+\/edit$/);

    await openAwards(page);
    const row = page.getByTestId('award-item').filter({ hasText: title });
    await expect(row.getByTestId('award-status')).toContainText('Чернетка');

    await row.locator('a').click();
    await expect(page.getByTestId('award-title-uk')).toHaveValue(title);
    await page.getByTestId('award-submit').click();
    await expect(page.getByTestId('award-form-error')).toContainText('заповніть категорію');

    await chooseCategory(page, 13);
    await page.getByTestId('award-organization').fill('Міністерство освіти і науки України');
    await page.getByTestId('award-date').fill(pastDay());
    await page.getByTestId('award-submit').click();

    await expect(page.getByTestId('award-submitted-text')).toContainText(
      'Подано на розгляд секретарю факультету',
    );
    await expect(page.getByTestId('award-submitted-name')).toContainText(title);
    await openAwards(page);
    await expect(
      page.getByTestId('award-item').filter({ hasText: title }).getByTestId('award-status'),
    ).toContainText('На розгляді');
  });

  test('ac1_10 an interrupted form is offered back and leaving asks first', async ({ page }) => {
    const title = `Подяка ректора ${Date.now()}`;
    await openAwards(page);
    await page.getByTestId('award-add').click();
    await page.getByTestId('award-title-uk').fill(title);
    await page.getByTestId('award-organization').fill('ЧНУ');
    await page.waitForTimeout(600);

    await page.reload();
    await expect(page.getByTestId('restore-offer')).toContainText('Відновити незбережені зміни?');
    await page.getByTestId('restore-accept').click();
    await expect(page.getByTestId('award-title-uk')).toHaveValue(title);

    await page.getByTestId('nav-awards').click();
    await page.getByTestId('confirm-cancel').click();
    await expect(page.getByRole('dialog')).toHaveCount(0);
    await expect(page).toHaveURL(/\/awards\/new$/);
    await expect(page.getByTestId('award-title-uk')).toHaveValue(title);

    await page.getByTestId('nav-awards').click();
    await page.getByTestId('confirm-accept').click();
    await expect(page).toHaveURL(/\/awards$/);
    await page.getByTestId('award-add').click();
    await expect(page.getByTestId('restore-offer')).toHaveCount(0);
  });

  test('ac2_3 ac2_6 the date picker is limited and a recent date is pointed out', async ({ page }) => {
    await openAwards(page);
    await page.getByTestId('award-add').click();
    const date = page.getByTestId('award-date');
    const max = (await date.getAttribute('max')) ?? '';
    expect(max).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    await expect(date).toHaveAttribute('min', `${Number(max.substring(0, 4)) - 50}${max.substring(4)}`);

    await date.fill(daysAgo(7));
    await expect(page.getByTestId('award-recent-date')).toContainText('за останні 30 днів');
    await date.fill(pastDay());
    await expect(page.getByTestId('award-recent-date')).toHaveCount(0);
  });

  test('ac2_4 ac2_5 ac2_6 a possible duplicate is submitted only after confirmation', async ({ page }) => {
    const title = `Грамота МОН ${token()} ${token()}`;
    const date = pastDay();
    await openAwards(page);
    await page.getByTestId('award-add').click();
    await fillComplete(page, title, date);
    await page.getByTestId('award-submit').click();
    await expect(page.getByTestId('award-submitted-name')).toContainText(title);

    await openAwards(page);
    await page.getByTestId('award-add').click();
    await fillComplete(page, `${title} України`, date);
    await page.getByTestId('award-submit').click();
    await expect(page.getByRole('dialog')).toContainText('Можливо, цю нагороду вже внесено');
    await expect(page.getByRole('dialog').getByRole('link', { name: title })).toBeVisible();
    await page.getByTestId('duplicate-cancel').click();
    await expect(page.getByTestId('award-form-message')).toContainText('Чернетку збережено');
    await expect(page.getByTestId('award-duplicate-warning')).toContainText(title);

    await page.getByTestId('award-submit').click();
    await page.getByTestId('duplicate-confirm').click();
    await expect(page.getByTestId('award-submitted-name')).toContainText(`${title} України`);
  });

  test('ac1_8 ac1_9 unknown awards are not found and the pages speak English', async ({ page }) => {
    await page.goto('/awards/abc');
    await expect(page.getByTestId('award-not-found')).toContainText('Не знайдено');
    await page.goto('/awards/999999999');
    await expect(page.getByTestId('award-not-found')).toBeVisible();

    await page.getByTestId('language-toggle').click();
    await openAwards(page);
    await expect(page.getByRole('heading', { name: 'My awards' })).toBeVisible();
    await expect(page.getByTestId('award-add')).toContainText('Add award');
  });
});
