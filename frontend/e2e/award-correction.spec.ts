import { Browser, expect, test } from '@playwright/test';

import {
  FRESH_PASSWORD,
  SEED,
  countMessages,
  freshEmployee,
  kyivDay,
  pastDay,
  seriousViolations,
  shownDay,
  signedIn,
  signedInAs,
  submitWithoutDocuments,
  uniqueToken,
} from './helpers';

const SUBJECT = 'Рецензент виправив нагороду';

async function submittedBy(
  browser: Browser,
  owner: string,
  title: string,
  date = pastDay(),
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
  await page.getByTestId('award-date').fill(date);
  await submitWithoutDocuments(page);
  await expect(page.getByTestId('award-submitted-name')).toContainText(title);
  await page.context().close();
  return id;
}

test.describe('award correction by a reviewer', () => {
  test('ac3_1 ac3_2 ac3_3 ac3_5 ac3_6 the secretary corrects a field and the owner sees it', async ({
    browser,
  }) => {
    const owner = await freshEmployee(browser, 'correct');
    const title = `Грамота для виправлення ${uniqueToken()}`;
    const id = await submittedBy(browser, owner, title);
    const secretary = await signedIn(browser, SEED.secretary);

    await secretary.goto(`/awards/${id}`);
    await secretary.getByTestId('review-correct').click();
    await expect(secretary).toHaveURL(new RegExp(`/awards/${id}/correct$`));
    await expect(secretary.getByTestId('award-title-uk')).toHaveValue(title);
    await expect(secretary.getByTestId('correction-save')).toBeDisabled();
    await secretary.getByTestId('award-organization').fill('Міністерство освіти України');
    await secretary.getByTestId('correction-reason').fill('Назву організації взято з наказу');
    await expect(secretary.getByTestId('correction-save')).toBeEnabled();
    await secretary.getByTestId('correction-save').click();
    await expect(secretary.getByTestId('correction-change')).toHaveText([
      /Міністерство освіти і науки України\s+→\s+Міністерство освіти України/,
    ]);
    await secretary.getByTestId('confirm-accept').click();

    await expect(secretary).toHaveURL(new RegExp(`/awards/${id}$`));
    await expect(secretary.getByTestId('review-panel-reviewer')).not.toHaveText(
      'Ще ніхто не взяв у роботу',
    );

    const ownerPage = await signedInAs(browser, owner, FRESH_PASSWORD);
    await ownerPage.goto(`/awards/${id}`);
    await expect(ownerPage.getByTestId('award-history-action').first()).toContainText(
      'Виправлено рецензентом:',
    );
    await expect(ownerPage.getByTestId('award-history-comment').first()).toHaveText(
      'Причина: Назву організації взято з наказу',
    );
    await expect(ownerPage.getByTestId('award-history-change').first()).toContainText(
      'Міністерство освіти України',
    );
    await expect.poll(() => countMessages(owner, SUBJECT), { timeout: 20_000 }).toBe(1);
  });

  test('ac3_8 the correction form works in English at 360 px by keyboard', async ({ browser }) => {
    const owner = await freshEmployee(browser, 'correct-en');
    const id = await submittedBy(browser, owner, `Грамота англійською ${uniqueToken()}`);
    const page = await signedIn(browser, SEED.secretary);
    await page.setViewportSize({ width: 360, height: 780 });
    await page.getByTestId('language-toggle').click();

    await page.goto(`/awards/${id}/correct`);
    await expect(page.getByRole('heading', { name: 'Correct the award' })).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(
      360,
    );
    expect(await seriousViolations(page)).toEqual([]);

    await page.getByTestId('award-title').focus();
    await page.keyboard.type('Letter of thanks');
    await page.getByTestId('correction-reason').focus();
    await page.keyboard.type('English title added');
    await page.keyboard.press('Tab');
    await page.keyboard.press('Tab');
    await expect(page.getByTestId('correction-save')).toBeFocused();
    await page.keyboard.press('Enter');
    await expect(page.getByRole('dialog')).toContainText('Send the correction?');
    expect(await seriousViolations(page)).toEqual([]);
    await page.getByTestId('confirm-accept').focus();
    await page.keyboard.press('Enter');

    await expect(page).toHaveURL(new RegExp(`/awards/${id}$`));
    await expect(page.getByTestId('award-history-action').first()).toContainText(
      'Corrected by reviewer:',
    );
  });

  test('ac1 ac2 ac3 ac4 reason message, day-first dates, translated calendar toggle, Back skips the form', async ({
    browser,
  }) => {
    const owner = await freshEmployee(browser, 'correct-walk');
    const before = kyivDay(-400);
    const after = kyivDay(-300);
    const id = await submittedBy(browser, owner, `Грамота з датою ${uniqueToken()}`, before);
    const secretary = await signedIn(browser, SEED.secretary);
    await secretary.goto(`/awards/${id}`);
    await secretary.getByTestId('review-correct').click();
    await expect(secretary).toHaveURL(new RegExp(`/awards/${id}/correct$`));

    await expect(secretary.getByRole('button', { name: 'Відкрити календар' })).toBeVisible();
    await secretary.getByTestId('award-date').fill(shownDay(after));
    await secretary.getByTestId('correction-save').click();
    await expect(secretary.getByText('Вкажіть причину виправлення')).toBeVisible();

    await secretary.getByTestId('correction-reason').fill('Дата за сертифікатом');
    await secretary.getByTestId('correction-save').click();
    await expect(secretary.getByTestId('correction-change')).toHaveText([
      new RegExp(`${shownDay(before)}\\s+→\\s+${shownDay(after)}`),
    ]);
    await secretary.getByTestId('confirm-accept').click();
    await expect(secretary).toHaveURL(new RegExp(`/awards/${id}$`));
    await expect(secretary.getByTestId('award-history-change').first()).toContainText(
      shownDay(after),
    );

    await secretary.goBack();
    await expect(secretary).not.toHaveURL(/\/correct$/);
  });
});
