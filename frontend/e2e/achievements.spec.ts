import { Browser, Page, expect, test } from '@playwright/test';

import {
  FRESH_PASSWORD,
  SEED,
  freshEmployee,
  pastDay,
  seriousViolations,
  signedIn,
  signedInAs,
  sql,
  submitWithoutDocuments,
  uniqueToken,
} from './helpers';

/** Submits an award as the owner and approves it in the database, dated today so it heads the list. */
async function approvedBy(browser: Browser, owner: string, title: string): Promise<string> {
  const page = await signedInAs(browser, owner, FRESH_PASSWORD);
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
  await page.context().close();
  sql(
    `update awards set status = 'APPROVED', award_date = current_date where award_id = ${id}; ` +
      `update award_requests set status = 'APPROVED', completed_at = now() where award_id = ${id}`,
  );
  return id;
}

async function openAward(page: Page, id: string): Promise<void> {
  await page.goto(`/awards/${id}`);
  await expect(page.getByTestId('award-visibility')).toBeVisible();
}

function card(page: Page, title: string) {
  return page.getByTestId('achievement-card').filter({ hasText: title });
}

test.describe('colleague visibility and the achievements page', () => {
  test('ac1_1 ac1_2 ac1_3 ac1_7 ac1_8 ac1_9 the owner shares an award and colleagues find it', async ({
    browser,
  }) => {
    const owner = await freshEmployee(browser, 'share');
    const title = `Грамота для колег ${uniqueToken()}`;
    const id = await approvedBy(browser, owner, title);
    const page = await signedInAs(browser, owner, FRESH_PASSWORD);

    await openAward(page, id);
    await expect(page.getByRole('radio', { name: 'Лише мені та рецензентам' })).toBeChecked();

    await page.getByRole('radio', { name: 'Показувати колегам' }).check();
    await expect(page.getByTestId('visibility-notice')).toHaveText('Видимість збережено.');

    await page.getByRole('radio', { name: 'Показувати публічно' }).check();
    await expect(page.getByRole('dialog')).toContainText('електронна пошта');
    await page.getByTestId('confirm-cancel').click();
    await expect(page.getByRole('radio', { name: 'Показувати колегам' })).toBeChecked();

    await page.getByRole('radio', { name: 'Показувати публічно' }).check();
    await page.getByTestId('confirm-accept').click();
    await expect(page.getByTestId('visibility-notice')).toHaveText('Видимість збережено.');
    await page.reload();
    await expect(page.getByRole('radio', { name: 'Показувати публічно' })).toBeChecked();

    await page.getByTestId('nav-awards').click();
    await expect(
      page.getByTestId('award-item').filter({ hasText: title }).getByTestId('award-visibility'),
    ).toHaveText('Публічно');

    const colleague = await signedIn(browser, SEED.employee);
    await colleague.getByTestId('nav-achievements').click();
    await expect(colleague).toHaveURL(/\/achievements$/);
    const shared = card(colleague, title);
    await expect(shared).toBeVisible();
    await expect(shared.getByTestId('achievement-recipient')).toContainText('Ірина Нова');
    await expect(shared.getByTestId('achievement-unit')).not.toBeEmpty();

    await colleague.getByTestId('filter-recipient').click();
    await colleague.getByRole('option', { name: 'Підрозділи' }).click();
    await expect(colleague).toHaveURL(/recipient=UNIT/);
    await expect(card(colleague, title)).toHaveCount(0);
    await colleague.reload();
    await expect(colleague.getByTestId('filter-recipient')).toContainText('Підрозділи');

    await colleague.goto('/achievements?recipient=PERSON&year=1976');
    await expect(colleague.getByTestId('achievements-empty')).toHaveText(
      'Поки що немає досягнень за цими умовами.',
    );

    await openAward(page, id);
    await page.getByRole('radio', { name: 'Лише мені та рецензентам' }).check();
    await expect(page.getByTestId('visibility-notice')).toHaveText('Видимість збережено.');
    await colleague.goto('/achievements');
    await expect(colleague.getByTestId('achievement-card').first()).toBeVisible();
    await expect(card(colleague, title)).toHaveCount(0);
  });

  test('ac1_1 a draft shows no visibility choice', async ({ browser }) => {
    const owner = await freshEmployee(browser, 'draft');
    const page = await signedInAs(browser, owner, FRESH_PASSWORD);
    await page.getByTestId('nav-awards').click();
    await page.getByTestId('award-add').click();
    await page.getByTestId('award-title-uk').fill(`Чернетка ${uniqueToken()}`);
    await page.getByTestId('award-save').click();
    await expect(page).toHaveURL(/\/awards\/\d+\/edit$/);
    const id = /\/awards\/(\d+)\/edit/.exec(page.url())?.[1] ?? '';

    await page.goto(`/awards/${id}`);
    await expect(page.getByTestId('award-detail')).toBeVisible();
    await expect(page.getByTestId('award-visibility')).toHaveCount(0);
  });

  test('ac1_8 the achievements page works in English at 360 px', async ({ browser }) => {
    const owner = await freshEmployee(browser, 'share-en');
    const title = `Подяка англійською ${uniqueToken()}`;
    const id = await approvedBy(browser, owner, title);
    sql(`update awards set visibility = 'UNIVERSITY' where award_id = ${id}`);
    const page = await signedIn(browser, SEED.employee);
    await page.setViewportSize({ width: 360, height: 780 });
    await page.getByTestId('language-toggle').click();

    await page.goto('/achievements');
    await expect(page.getByRole('heading', { name: 'Achievements', level: 1 })).toBeVisible();
    await expect(card(page, title).getByTestId('achievement-title')).toHaveText(title);
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(
      360,
    );
    expect(await seriousViolations(page)).toEqual([]);

    await page.getByTestId('filter-unit').focus();
    await page.keyboard.press('Enter');
    await expect(page.getByRole('listbox')).toBeVisible();
    await page.keyboard.press('Escape');
  });
});
