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

/** A small PDF that differs from every other one, so that no upload is refused as a duplicate. */
function pdf(): Buffer {
  const content = Buffer.alloc(2048, 0x20);
  content.write(`%PDF-1.4
% ${uniqueToken()}
`);
  return content;
}

/**
 * Submits an award as the owner, with the given PDF documents, and approves it in the database, dated today so
 * it heads the list.
 */
async function approvedBy(
  browser: Browser,
  owner: string,
  title: string,
  documents: string[] = [],
): Promise<string> {
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
  for (const name of documents) {
    await page
      .getByTestId('documents-input')
      .setInputFiles({ name, mimeType: 'application/pdf', buffer: pdf() });
    await expect(page.getByTestId('documents-list')).toContainText(name, { timeout: 30_000 });
  }
  if (documents.length) {
    await page.getByTestId('award-submit').click();
  } else {
    await submitWithoutDocuments(page);
  }
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
    const listed = colleague.waitForResponse(
      (response) => response.url().includes('/api/v1/achievements') && response.ok(),
    );
    await colleague.goto('/achievements');
    await listed;
    await expect(
      colleague
        .getByTestId('achievement-card')
        .or(colleague.getByTestId('achievements-empty'))
        .first(),
    ).toBeVisible();
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

/** A public and a colleagues-only award of a fresh owner, both approved today. */
async function publicAndShared(
  browser: Browser,
  name: string,
): Promise<{ published: string; shared: string; unit: string }> {
  const owner = await freshEmployee(browser, name);
  const published = `Публічна грамота ${uniqueToken()}`;
  const shared = `Грамота для колег ${uniqueToken()}`;
  const publishedId = await approvedBy(browser, owner, published);
  const sharedId = await approvedBy(browser, owner, shared);
  sql(`update awards set visibility = 'PUBLIC' where award_id = ${publishedId}`);
  sql(`update awards set visibility = 'UNIVERSITY' where award_id = ${sharedId}`);
  const unit = sql(`select organization_id from awards where award_id = ${publishedId}`).trim();
  return { published, shared, unit };
}

/** A page without a session that records every console error. */
async function anonymous(browser: Browser): Promise<{ page: Page; errors: string[] }> {
  const page = await (await browser.newContext()).newPage();
  const errors: string[] = [];
  page.on('console', (message) => {
    if (message.type() === 'error') {
      errors.push(message.text());
    }
  });
  return { page, errors };
}

test.describe('unit achievement pages and public achievements', () => {
  test('ac2_1 ac2_4 ac2_5 ac2_8 a visitor without an account browses the public pages', async ({
    browser,
  }) => {
    const { published, shared, unit } = await publicAndShared(browser, 'public');
    const { page, errors } = await anonymous(browser);

    await page.goto('/public/achievements');
    await expect(
      page.getByRole('heading', { name: 'Досягнення університету', level: 1 }),
    ).toBeVisible();
    await expect(card(page, published)).toBeVisible();
    await expect(card(page, shared)).toHaveCount(0);
    await expect(page.getByTestId('login')).toBeVisible();
    await expect(page.getByTestId('language-toggle')).toBeVisible();
    await expect(page.getByTestId('nav-achievements')).toHaveCount(0);
    await expect(page.getByTestId('achievements-counterpart')).toHaveCount(0);
    expect(await seriousViolations(page)).toEqual([]);

    await card(page, published).getByTestId('achievement-unit').click();
    await expect(page).toHaveURL(new RegExp(`/public/units/${unit}/achievements$`));
    await expect(page.getByTestId('unit-name')).not.toBeEmpty();
    await expect(card(page, published)).toBeVisible();
    await expect(page.getByTestId('filter-unit')).toHaveCount(0);

    await page.getByTestId('unit-faculty').click();
    await expect(page).toHaveURL(/\/public\/units\/9\/achievements$/);
    await expect(page.getByTestId('unit-department').first()).toBeVisible();
    await expect(card(page, published)).toBeVisible();
    expect(await seriousViolations(page)).toEqual([]);
    expect(errors).toEqual([]);

    await page.goto('/public/units/1/achievements');
    await expect(page.getByTestId('not-found-card')).toBeVisible();
    await expect(page).toHaveURL(/\/public\/units\/1\/achievements$/);
  });

  test('ac2_6 staff and public pages lead to each other with the same filters', async ({
    browser,
  }) => {
    const { published, shared, unit } = await publicAndShared(browser, 'links');
    const year = new Date().getFullYear();
    const page = await signedIn(browser, SEED.employee);

    await page.goto(`/achievements?year=${year}`);
    await card(page, shared).getByTestId('achievement-unit').click();
    await expect(page).toHaveURL(new RegExp(`/units/${unit}/achievements`));
    await expect(card(page, shared)).toBeVisible();
    await expect(card(page, published)).toBeVisible();

    await page.goto(`/achievements?year=${year}`);
    await page.getByTestId('achievements-counterpart').click();
    await expect(page).toHaveURL(new RegExp(`/public/achievements\\?year=${year}$`));
    await expect(card(page, published)).toBeVisible();
    await expect(card(page, shared)).toHaveCount(0);

    await expect(page.getByTestId('achievements-counterpart')).toHaveText(
      'Переглянути як співробітник',
    );
    await page.getByTestId('achievements-counterpart').click();
    await expect(page).toHaveURL(new RegExp(`/achievements\\?year=${year}$`));
    expect(page.url()).not.toContain('/public/');
    await expect(card(page, shared)).toBeVisible();
  });

  test('ac2_8 a public unit page works in English at 360 px', async ({ browser }) => {
    const { published } = await publicAndShared(browser, 'public-en');
    const { page, errors } = await anonymous(browser);
    await page.setViewportSize({ width: 360, height: 780 });
    await page.goto('/public/achievements');
    await page.getByTestId('language-toggle').click();

    await page.goto('/public/units/9/achievements');
    await expect(page.getByTestId('login')).toHaveText(/Sign in/);
    await expect(page.getByTestId('unit-name')).not.toHaveText(/[а-яіїєґ]/i);
    await expect(card(page, published).getByTestId('achievement-title')).toHaveText(published);
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(
      360,
    );
    expect(await seriousViolations(page)).toEqual([]);
    expect(errors).toEqual([]);
  });
});

test.describe('documents shown to colleagues', () => {
  test('ac1 ac2 ac3 the owner shows a document and a colleague downloads it until the award is private', async ({
    browser,
  }) => {
    test.slow();
    const owner = await freshEmployee(browser, 'shown');
    const title = `Грамота з документами для колег ${uniqueToken()}`;
    const id = await approvedBy(browser, owner, title, ['диплом.pdf', 'довідка.pdf']);
    const page = await signedInAs(browser, owner, FRESH_PASSWORD);
    const shown = page.getByRole('switch', { name: 'Показувати колегам: диплом.pdf' });

    await openAward(page, id);
    await expect(page.getByTestId('documents-sharing-hint')).toHaveText(
      'Відкрийте нагороду для колег, щоб показати їм її документи.',
    );
    await expect(page.getByTestId('document-share')).toHaveCount(0);

    await page.getByRole('radio', { name: 'Показувати колегам' }).check();
    await expect(page.getByTestId('visibility-notice')).toHaveText('Видимість збережено.');
    await expect(page.getByTestId('document-share')).toHaveCount(2);
    await expect(shown).not.toBeChecked();
    await shown.focus();
    await page.keyboard.press('Space');
    await expect(shown).toBeChecked();
    await page.reload();
    await expect(shown).toBeChecked();
    await expect(
      page.getByRole('switch', { name: 'Показувати колегам: довідка.pdf' }),
    ).not.toBeChecked();

    const colleague = await signedIn(browser, SEED.employee);
    await colleague.setViewportSize({ width: 360, height: 780 });
    await colleague.goto('/achievements');
    await card(colleague, title).getByTestId('achievement-open').click();
    await expect(colleague).toHaveURL(new RegExp(`/achievements/${id}$`));
    await expect(colleague.getByTestId('achievement-detail-title')).toHaveText(title);
    await expect(colleague.getByTestId('shared-documents')).toContainText('диплом.pdf');
    await expect(colleague.getByTestId('shared-documents')).not.toContainText('довідка.pdf');
    await expect(colleague.getByTestId('award-history-tabs')).toHaveCount(0);
    expect(
      await colleague.evaluate(() => document.documentElement.scrollWidth),
    ).toBeLessThanOrEqual(360);
    expect(await seriousViolations(colleague)).toEqual([]);

    const download = colleague.waitForEvent('download');
    await colleague.getByRole('button', { name: 'Завантажити диплом.pdf' }).click();
    expect((await download).suggestedFilename()).toBe('диплом.pdf');

    await openAward(page, id);
    await page.getByRole('radio', { name: 'Лише мені та рецензентам' }).check();
    await expect(page.getByTestId('visibility-notice')).toHaveText('Видимість збережено.');
    await expect(page.getByTestId('document-share')).toHaveCount(0);
    expect(
      sql(`select count(*) from documents where award_id = ${id} and shown_to_colleagues`).trim(),
    ).toBe('0');

    await colleague.reload();
    await expect(colleague.getByTestId('achievement-detail-error')).toHaveText(
      'Це досягнення недоступне. Можливо, його більше не показують колегам.',
    );
  });
});
