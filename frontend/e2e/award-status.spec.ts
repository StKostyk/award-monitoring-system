import { Page, expect, test } from '@playwright/test';

import {
  kyivDay,
  pastDay,
  shownDay,
  signIn,
  sql,
  submitWithoutDocuments,
  uniqueToken,
} from './helpers';

const demo = 'Passw0rd-demo';
const employee = 'employee.fmi@chnu.edu.ua';
const secretary = 'secretary.fmi@chnu.edu.ua';

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

async function openAward(page: Page, id: string): Promise<void> {
  const status = page.waitForResponse((response) =>
    response.url().endsWith(`/awards/${id}/status`),
  );
  await page.goto(`/awards/${id}`);
  await status;
}

test.describe('award status tracking', () => {
  test.beforeEach(async ({ page }) => {
    page.on('dialog', (dialog) => void dialog.accept());
    await signIn(page, employee, demo);
    await expect(page.getByTestId('nav-awards')).toBeVisible();
  });

  test('ac1_12 ac1_17 ac1_19 a submitted award shows its path, estimate and home card', async ({
    page,
  }) => {
    const title = `Грамота МОН ${uniqueToken()}`;
    const id = await submitted(page, title);

    await openAward(page, id);
    const steps = page.getByTestId('award-status-step');
    await expect(page.getByTestId('award-status-path')).toContainText('Подано');
    await expect(steps).toHaveText([/Секретар факультету/, /Декан/, /Секретар ректора/]);
    await expect(steps.first()).toHaveAttribute('aria-current', 'step');
    await expect(steps.first()).toContainText(/Очікується до \d{2}\.\d{2}\.\d{4}/);
    await expect(page.getByTestId('award-status-estimate')).toContainText(
      /Орієнтовне завершення: \d{2}\.\d{2}\.\d{4}/,
    );
    await expect(page.getByTestId('award-status-delay')).toHaveCount(0);

    await page.goto('/');
    const card = page.getByTestId('my-submission').filter({ hasText: title });
    await expect(card).toContainText('Секретар факультету');
    await expect(card).toContainText(/Очікується до \d{2}\.\d{2}\.\d{4}/);
    await card.getByRole('link').click();
    await expect(page).toHaveURL(new RegExp(`/awards/${id}$`));

    await page.getByTestId('language-toggle').click();
    await expect(page.getByTestId('award-status-panel')).toContainText('Review status');
    await expect(page.getByTestId('award-status-step').first()).toContainText('Faculty secretary');
    await expect(page.getByTestId('award-status-estimate')).toContainText('Expected completion');
    await page.getByTestId('language-toggle').click();
  });

  test('ac1_13 ac1_18 an overdue review is explained on the page, the list and the card', async ({
    page,
  }) => {
    const title = `Подяка ${uniqueToken()}`;
    const id = await submitted(page, title);
    sql(`update award_requests set deadline = now() - interval '2 days' where award_id = ${id}`);

    await openAward(page, id);
    await expect(page.getByTestId('award-status-delay')).toContainText(
      /Розгляд триває довше, ніж зазвичай \(з \d{2}\.\d{2}\.\d{4}\)\. Нова орієнтовна дата: /,
    );
    await expect(page.getByTestId('award-status-due')).toHaveText(
      new RegExp(`Очікується до (${shownDay(kyivDay(3))}|${shownDay(kyivDay(4))})`),
    );

    await page.getByTestId('nav-awards').click();
    const row = page.getByTestId('award-item').filter({ hasText: title });
    await expect(row.getByTestId('request-delayed')).toContainText('Затримка');
    await expect(row.getByTestId('request-timing')).toContainText(/Очікується до/);
  });

  test('ac1_14 ac1_15 a decision made while the page is open appears within the interval', async ({
    page,
  }) => {
    const title = `Диплом ${uniqueToken()}`;
    const id = await submitted(page, title);
    await page.clock.install();
    await openAward(page, id);
    await expect(page.getByTestId('award-status-estimate')).toBeVisible();

    const reviewer = sql(`select user_id from users where email_address = '${secretary}'`);
    sql(
      `insert into review_decisions (request_id, reviewer_id, decision, level, comments) select request_id, ` +
        `${reviewer}, 'RETURNED', 'FACULTY_SECRETARY', 'Додайте номер наказу' from award_requests ` +
        `where award_id = ${id}; update award_requests set status = 'RETURNED' where award_id = ${id}`,
    );
    const reloaded = page.waitForResponse((response) =>
      response.url().endsWith(`/awards/${id}/status`),
    );
    await page.clock.fastForward('01:01');
    await reloaded;

    await expect(page.getByTestId('award-status-announcement')).toContainText(
      'Статус розгляду оновлено',
    );
    await expect(page.getByTestId('award-status-returned')).toContainText(
      'Очікує ваших виправлень',
    );
    await expect(page.getByTestId('award-status-estimate')).toHaveCount(0);
    const decision = page.getByTestId('award-status-decision');
    await expect(decision).toContainText('Повернуто на доопрацювання');
    await expect(decision).toContainText('Секретар факультету');
    await expect(page.getByTestId('award-status-comment')).toContainText('Додайте номер наказу');

    await page.goto('/');
    await expect(page.getByTestId('my-submission').filter({ hasText: title })).toContainText(
      'Очікує ваших виправлень',
    );
    await page.getByTestId('nav-awards').click();
    await expect(
      page.getByTestId('award-item').filter({ hasText: title }).getByTestId('request-timing'),
    ).toContainText('Очікує ваших виправлень');
  });
});
