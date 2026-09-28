import { expect, test } from '@playwright/test';

import { signIn } from './helpers';

const demo = 'Passw0rd-demo';
const employee = 'employee.fmi@chnu.edu.ua';

type Page = import('@playwright/test').Page;

function lastYear(): string {
  const date = new Date();
  date.setFullYear(date.getFullYear() - 1);
  return date.toISOString().substring(0, 10);
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
    const title = `Грамота Міністерства освіти і науки ${Date.now()}`;
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
    await page.getByTestId('award-date').fill(lastYear());
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
    await expect(page).toHaveURL(/\/awards\/new$/);
    await expect(page.getByTestId('award-title-uk')).toHaveValue(title);

    await page.getByTestId('nav-awards').click();
    await page.getByTestId('confirm-accept').click();
    await expect(page).toHaveURL(/\/awards$/);
    await page.getByTestId('award-add').click();
    await expect(page.getByTestId('restore-offer')).toHaveCount(0);
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
