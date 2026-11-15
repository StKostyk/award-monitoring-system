import { Page } from '@playwright/test';

import { expect, test } from './fixtures';

import {
  FRESH_PASSWORD,
  SEED,
  freshEmployee,
  pastDay,
  seriousViolations,
  signIn,
  signInAsSeed,
  uniqueToken,
} from './helpers';

async function openWithShortcut(page: Page): Promise<void> {
  await expect(page.getByTestId('palette-open')).toBeVisible();
  await page.keyboard.press('ControlOrMeta+k');
  await expect(page.getByTestId('palette-input')).toBeFocused();
}

async function createDraft(page: Page, title: string): Promise<string> {
  await page.goto('/awards/new');
  await page.getByTestId('award-title-uk').fill(title);
  await page.getByTestId('award-category').click();
  await page.getByTestId('category-option-13').click();
  await page.getByTestId('award-organization').fill('Міністерство освіти і науки України');
  await page.getByTestId('award-date').fill(pastDay());
  await page.getByTestId('award-save').click();
  await expect(page.getByTestId('award-form-message')).toBeVisible();
  await expect(page).toHaveURL(/\/awards\/\d+/);
  return /\/awards\/(\d+)/.exec(page.url())![1];
}

test.describe('command palette', () => {
  test('ac3_4 ac3_6 an employee opens it with the shortcut and sees no administration', async ({
    page,
  }) => {
    await signInAsSeed(page, SEED.employee);
    const trigger = page.getByTestId('palette-open');
    await trigger.focus();

    await openWithShortcut(page);
    const dialog = page.getByRole('dialog');
    await expect(dialog.getByTestId('palette-section-awards')).toBeVisible();
    await expect(dialog.getByTestId('palette-section-reviews')).toHaveCount(0);
    await expect(dialog.getByTestId('palette-section-users')).toHaveCount(0);
    await expect(dialog.getByTestId('palette-section-compliance')).toHaveCount(0);
    expect(await seriousViolations(page)).toEqual([]);

    await page.keyboard.press('ControlOrMeta+k');
    await expect(page.getByRole('dialog')).toHaveCount(1);

    await page.keyboard.press('Escape');
    await expect(page.getByRole('dialog')).toHaveCount(0);
    await expect(trigger).toBeFocused();
  });

  test('ac3_4 ac3_5 a secretary opens it from the toolbar and goes to reviews with the keys', async ({
    page,
  }) => {
    await signInAsSeed(page, SEED.secretary);
    await page.getByTestId('palette-open').click();
    await expect(page.getByTestId('palette-input')).toBeFocused();

    await page.getByTestId('palette-input').fill('розгляд');
    await expect(page.locator('[role="option"]')).toHaveCount(1);
    await expect(page.getByTestId('palette-section-reviews')).toHaveAttribute(
      'aria-selected',
      'true',
    );
    await page.keyboard.press('Enter');

    await expect(page).toHaveURL(/\/reviews$/);
    await expect(page.getByRole('dialog')).toHaveCount(0);
  });

  test('ac3_5 ac3_6 finds the own award by title, and nobody else sees it', async ({
    browser,
    page,
  }) => {
    const email = await freshEmployee(browser, 'palette');
    await signIn(page, email, FRESH_PASSWORD);
    const token = uniqueToken();
    const id = await createDraft(page, `Подяка палітри ${token}`);
    await page.goto('/');

    await openWithShortcut(page);
    await page.getByTestId('palette-input').fill(token);
    const found = page.getByTestId(`palette-award-${id}`);
    await expect(found).toBeVisible();
    await page.keyboard.press('ArrowDown');
    await expect(found).toHaveAttribute('aria-selected', 'true');
    await page.keyboard.press('Enter');
    await expect(page).toHaveURL(new RegExp(`/awards/${id}$`));

    const other = await (await browser.newContext()).newPage();
    await signInAsSeed(other, SEED.lecturer);
    await openWithShortcut(other);
    await other.getByTestId('palette-input').fill(token);
    await expect(other.getByTestId('palette-empty')).toBeVisible();
    await expect(other.getByTestId(`palette-award-${id}`)).toHaveCount(0);
    await other.context().close();
  });
});
