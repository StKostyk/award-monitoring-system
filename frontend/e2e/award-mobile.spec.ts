import { expect, test } from './fixtures';

import { FRESH_PASSWORD, freshEmployee, pastDay, signIn, uniqueToken } from './helpers';

test.describe('award form on a phone', () => {
  test('ac3_5 ac3_6 an award is created, photographed and submitted', async ({ browser, page }) => {
    const email = await freshEmployee(browser, 'phone');
    await signIn(page, email, FRESH_PASSWORD);
    await expect(page.getByTestId('nav-toggle')).toBeVisible();

    await page.goto('/awards/new');
    await page.getByTestId('award-title-uk').fill(`Грамота з телефона ${uniqueToken()}`);
    await page.getByTestId('award-category').click();
    await page.getByTestId('category-option-13').click();
    await page.getByTestId('award-organization').fill('Міністерство освіти і науки України');
    await page.getByTestId('award-date').fill(pastDay());
    await page.getByTestId('award-save').click();
    await expect(page.getByTestId('award-form-message')).toContainText('Чернетку збережено');
    expect(
      await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
    ).toBe(true);

    const photo = await page.screenshot({
      type: 'jpeg',
      clip: { x: 0, y: 0, width: 48, height: 48 },
    });
    const camera = page.getByTestId('documents-camera-input');
    await expect(camera).toHaveAttribute('capture', 'environment');
    await camera.setInputFiles({ name: 'грамота.jpg', mimeType: 'image/jpeg', buffer: photo });
    const stored = page.getByTestId('documents-list').locator('li');
    await expect(stored).toHaveCount(1);
    await stored.getByTestId('document-name').click();
    await expect(page.getByTestId('document-preview-image')).toBeVisible();
    await page.getByTestId('document-preview-close').click();

    await camera.setInputFiles({
      name: 'фото.heic',
      mimeType: 'image/heic',
      buffer: Buffer.from('heic'),
    });
    await expect(page.getByTestId('documents-queued-problem')).toContainText(
      'PDF, JPG, PNG і WEBP',
    );
    await page.getByTestId('documents-dismiss').click();

    await page.getByTestId('award-submit').click();
    await expect(page).toHaveURL(/\/awards\/\d+\/submitted$/);
  });

  test('ac1_2 the list shows the search and opens the other filters from a button', async ({
    browser,
    page,
  }) => {
    const email = await freshEmployee(browser, 'phone-list');
    await signIn(page, email, FRESH_PASSWORD);

    await page.goto('/awards');
    const toggle = page.getByTestId('filters-toggle');
    await expect(page.locator('app-search-field input')).toBeVisible();
    await expect(toggle).toHaveAttribute('aria-expanded', 'false');
    await expect(page.getByTestId('filter-status')).toHaveCount(0);

    await toggle.click();
    await expect(toggle).toHaveAttribute('aria-expanded', 'true');
    await expect(page.getByTestId('filter-status')).toBeVisible();
    await expect(page.getByTestId('filter-date-to')).toBeVisible();
  });
});
