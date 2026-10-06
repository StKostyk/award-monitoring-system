import { expect, test } from '@playwright/test';

import {
  DEMO_PASSWORD,
  SEED,
  kyivDay,
  pastDay,
  signIn,
  submitWithoutDocuments,
  uniqueToken,
} from './helpers';

type Page = import('@playwright/test').Page;

const { employee } = SEED;
async function fillComplete(page: Page, title: string, date: string): Promise<void> {
  await page.getByTestId('award-title-uk').fill(title);
  await chooseCategory(page, 13);
  await page.getByTestId('award-organization').fill('Міністерство освіти і науки України');
  await page.getByTestId('award-date').fill(date);
}

async function openAwards(page: Page): Promise<void> {
  const loaded = page.waitForResponse(
    (response) =>
      response.request().method() === 'GET' && /\/api\/v1\/awards\?/.test(response.url()),
  );
  await clickMyAwards(page);
  await loaded;
}

/** Clicks «Мої нагороди» in the side menu, opening the drawer first on a phone. */
async function clickMyAwards(page: Page): Promise<void> {
  const link = page.getByTestId('nav-awards');
  await page
    .locator('mat-sidenav')
    .evaluate((drawer) =>
      Promise.all(drawer.getAnimations().map((animation) => animation.finished)),
    );
  if (!(await link.isVisible())) {
    await page.getByTestId('nav-toggle').click();
  }
  await link.click();
}

function storedCopies(page: Page): Promise<number> {
  return page.evaluate(
    () => Object.keys(localStorage).filter((key) => key.startsWith('awards.form-copy.')).length,
  );
}

async function chooseCategory(page: Page, id: number): Promise<void> {
  await page.getByTestId('award-category').click();
  await page.getByTestId(`category-option-${id}`).click();
}

test.describe('award drafts and submission on a phone', () => {
  test.use({ viewport: { width: 360, height: 740 } });

  test.beforeEach(async ({ page }) => {
    page.on('dialog', (dialog) => void dialog.accept());
    await signIn(page, employee, DEMO_PASSWORD);
    await expect(page.getByTestId('nav-toggle')).toBeVisible();
  });

  test('ac1_1 ac1_5 ac1_9 a draft is saved, completed and submitted', async ({ page }) => {
    const title = `Грамота МОН ${uniqueToken()}`;
    await openAwards(page);
    await page.getByTestId('award-add').click();
    await expect(page).toHaveURL(/\/awards\/new$/);
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(
      360,
    );

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
    await submitWithoutDocuments(page);

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
    await expect.poll(() => storedCopies(page)).toBeGreaterThan(0);

    await page.reload();
    await expect(page.getByTestId('restore-offer')).toContainText('Відновити незбережені зміни?');
    await page.getByTestId('restore-accept').click();
    await expect(page.getByTestId('award-title-uk')).toHaveValue(title);

    await clickMyAwards(page);
    await page.getByTestId('confirm-cancel').click();
    await expect(page.getByRole('dialog')).toHaveCount(0);
    await expect(page.getByRole('dialog')).toHaveCount(0);
    await expect(page).toHaveURL(/\/awards\/new$/);
    await expect(page.getByTestId('award-title-uk')).toHaveValue(title);

    await clickMyAwards(page);
    await page.getByTestId('confirm-accept').click();
    await expect(page).toHaveURL(/\/awards$/);
    await page.getByTestId('award-add').click();
    await expect(page.getByTestId('restore-offer')).toHaveCount(0);
  });

  test('ac1_10 f4 signing out removes the unsaved copy', async ({ page }) => {
    await openAwards(page);
    await page.getByTestId('award-add').click();
    await page.getByTestId('award-title-uk').fill(`Подяка декана ${uniqueToken()}`);
    await expect.poll(() => storedCopies(page)).toBeGreaterThan(0);

    await page.getByTestId('logout').click();
    await expect(page).toHaveURL(/localhost:8080\/login/, { timeout: 15_000 });
    await signIn(page, employee, DEMO_PASSWORD);
    await expect(page.getByTestId('nav-toggle')).toBeVisible();
    await page.goto('/awards/new');

    await expect(page.getByTestId('award-title-uk')).toBeVisible();
    await expect(page.getByTestId('restore-offer')).toHaveCount(0);
    expect(await storedCopies(page)).toBe(0);
  });

  test('f9 a draft is deleted from its form after confirmation', async ({ page }) => {
    const title = `Чернетка на видалення ${uniqueToken()}`;
    await openAwards(page);
    await page.getByTestId('award-add').click();
    await expect(page.getByTestId('award-remove')).toHaveCount(0);
    await page.getByTestId('award-title-uk').fill(title);
    await page.getByTestId('award-save').click();
    await expect(page).toHaveURL(/\/awards\/\d+\/edit$/);
    const id = /\/awards\/(\d+)\/edit$/.exec(page.url())?.[1];

    await page.getByTestId('award-remove').click();
    await page.getByTestId('confirm-cancel').click();
    await expect(page.getByRole('dialog')).toHaveCount(0);
    await expect(page).toHaveURL(/\/edit$/);
    await page.getByTestId('award-remove').click();
    await page.getByTestId('confirm-accept').click();

    await expect(page).toHaveURL(/\/awards$/);
    await expect(page.getByTestId('awards-notice')).toContainText('Чернетку видалено');
    await expect(page.getByTestId('award-item').filter({ hasText: title })).toHaveCount(0);
    await page.goto(`/awards/${id}`);
    await expect(page.getByTestId('award-not-found')).toBeVisible();
  });

  test('m1 m2 an incomplete submission still saves the draft and cancel leads back', async ({
    page,
  }) => {
    await openAwards(page);
    await page.getByTestId('award-add').click();
    await page.getByTestId('award-title-uk').fill(`Подяка кафедри ${uniqueToken()}`);
    await page.getByTestId('award-submit').click();
    await expect(page.getByTestId('award-form-error')).toContainText('заповніть категорію');

    await page.getByTestId('award-save').click();
    await expect(page.getByTestId('award-form-message')).toContainText('Чернетку збережено');
    await expect(page).toHaveURL(/\/awards\/\d+\/edit$/);
    const id = /\/awards\/(\d+)\/edit$/.exec(page.url())?.[1];
    await page.getByTestId('award-cancel').click();
    await expect(page).toHaveURL(new RegExp(`/awards/${id}$`));

    await page.goto('/awards/new');
    await page.getByTestId('award-title-uk').fill('Подяка');
    await page.getByTestId('award-cancel').click();
    await page.getByTestId('confirm-accept').click();
    await expect(page).toHaveURL(/\/awards$/);
  });

  test('f1 the confirmation page confirms only an own submitted award', async ({ page }) => {
    await openAwards(page);
    await page.getByTestId('award-add').click();
    await page.getByTestId('award-title-uk').fill(`Недопрацьована ${uniqueToken()}`);
    await page.getByTestId('award-save').click();
    await expect(page).toHaveURL(/\/awards\/\d+\/edit$/);
    const id = /\/awards\/(\d+)\/edit$/.exec(page.url())?.[1];

    await page.goto(`/awards/${id}/submitted`);
    await expect(page).toHaveURL(new RegExp(`/awards/${id}/edit$`));
    await expect(page.getByTestId('award-submitted')).toHaveCount(0);

    await page.goto('/awards/999999999/submitted');
    await expect(page.getByTestId('award-not-found')).toBeVisible();
    await expect(page.getByTestId('award-submitted')).toHaveCount(0);
  });

  test('ac2_3 ac2_6 the date picker is limited and a recent date is pointed out', async ({
    page,
  }) => {
    await openAwards(page);
    await page.getByTestId('award-add').click();
    const date = page.getByTestId('award-date');
    const max = (await date.getAttribute('max')) ?? '';
    expect(max).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    await expect(date).toHaveAttribute(
      'min',
      `${Number(max.substring(0, 4)) - 50}${max.substring(4)}`,
    );

    await date.fill(kyivDay(-7));
    await expect(page.getByTestId('award-recent-date')).toContainText('за останні 30 днів');
    await date.fill(pastDay());
    await expect(page.getByTestId('award-recent-date')).toHaveCount(0);
  });

  test('ac3_4 suggested categories appear as chips and never replace a chosen category', async ({
    page,
  }) => {
    await openAwards(page);
    await page.getByTestId('award-add').click();
    const chips = page.locator('[data-testid^="category-suggestion-"]');

    await page.getByTestId('award-title').fill('Best paper award');
    await page
      .getByTestId('award-organization')
      .fill('IEEE International Conference on Software Engineering');
    await expect(chips.first()).toContainText('Міжнародний');
    expect(await chips.count()).toBeLessThanOrEqual(3);
    await chips.first().click();
    await expect(page.getByTestId('award-category')).toContainText(
      'Найкраща стаття міжнародної конференції',
    );
    await expect(chips).toHaveCount(0);

    await page.getByTestId('award-title').fill('');
    await chooseCategory(page, 13);
    await page.getByTestId('award-title-uk').fill('Подяка');
    await page.getByTestId('award-organization').fill('Факультет математики та інформатики ЧНУ');
    await page.waitForResponse((response) =>
      response.url().includes('/award-categories/suggestions'),
    );
    await expect(page.getByTestId('award-category')).toContainText('Відзнака міністерства');
    await expect(chips).toHaveCount(0);

    await page.getByTestId('award-category').click();
    await page.getByTestId('category-option-none').click();
    await expect(chips.first()).toContainText('Факультетський');
    await expect(chips.first()).toContainText('підрозділ університету');
  });

  test('ac3_4 no chips when the suggestion service fails', async ({ page }) => {
    await page.route('**/api/v1/award-categories/suggestions**', (route) =>
      route.fulfill({ status: 500, body: '' }),
    );
    await openAwards(page);
    await page.getByTestId('award-add').click();
    const failed = page.waitForResponse((response) =>
      response.url().includes('/award-categories/suggestions'),
    );
    await page.getByTestId('award-title').fill('Best paper award');
    await failed;

    await expect(page.locator('[data-testid^="category-suggestion-"]')).toHaveCount(0);
    await expect(page.getByTestId('award-title')).toHaveValue('Best paper award');
  });

  test('ac2_4 ac2_5 ac2_6 a possible duplicate is submitted only after confirmation', async ({
    page,
  }) => {
    const title = `Грамота МОН ${uniqueToken()} ${uniqueToken()}`;
    const date = pastDay();
    await openAwards(page);
    await page.getByTestId('award-add').click();
    await fillComplete(page, title, date);
    await submitWithoutDocuments(page);
    await expect(page.getByTestId('award-submitted-name')).toContainText(title);

    await openAwards(page);
    await page.getByTestId('award-add').click();
    await fillComplete(page, `${title} України`, date);
    await submitWithoutDocuments(page);
    await expect(page.getByRole('dialog')).toContainText('Можливо, цю нагороду вже внесено');
    await expect(page.getByRole('dialog').getByRole('link', { name: title })).toBeVisible();
    await page.getByTestId('duplicate-cancel').click();
    await expect(page.getByTestId('award-form-message')).toContainText('Чернетку збережено');
    await expect(page.getByTestId('award-duplicate-warning')).toContainText(title);

    await submitWithoutDocuments(page);
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
