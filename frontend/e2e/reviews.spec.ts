import { Browser, Page, expect, test } from '@playwright/test';

import {
  FRESH_PASSWORD,
  freshEmployee,
  pastDay,
  seriousViolations,
  signedInAs,
  sql,
  submitWithoutDocuments,
  uniqueToken,
} from './helpers';

/** A faculty without seed reviewers or awards, so the queue holds only what this spec submits. */
const FACULTY = 5;
const DEPARTMENT = 41;
/** A national category: a faculty secretary's approval passes it on. */
const NATIONAL = 13;
/** A university category: any reviewing level approves it for good. */
const UNIVERSITY = 21;

function grant(email: string, role: string, lastName: string): void {
  sql(
    `insert into user_roles (user_id, organization_id, role_type) select user_id, ${FACULTY}, '${role}' from users where email_address = '${email}'`,
  );
  sql(`update users set last_name = '${lastName}' where email_address = '${email}'`);
}

function user(email: string): string {
  return `(select user_id from users where email_address = '${email}')`;
}

async function submittedBy(
  browser: Browser,
  owner: string,
  title: string,
  category = NATIONAL,
): Promise<string> {
  const page = await signedInAs(browser, owner, FRESH_PASSWORD);
  await page.goto('/awards/new');
  await page.getByTestId('award-title-uk').fill(title);
  await page.getByTestId('award-category').click();
  await page.getByTestId(`category-option-${category}`).click();
  await page.getByTestId('award-organization').fill('Міністерство освіти і науки України');
  await page.getByTestId('award-date').fill(pastDay());
  await submitWithoutDocuments(page);
  await expect(page.getByTestId('award-submitted-name')).toContainText(title);
  await page.context().close();
  return sql(`select award_id from awards where title_uk = '${title}'`);
}

async function openAward(page: Page, id: string): Promise<void> {
  const item = page.waitForResponse((response) =>
    response.url().endsWith(`/awards/${id}/reviewer`),
  );
  await page.goto(`/awards/${id}`);
  await item;
}

test.describe('reviewer queue', () => {
  let first: string;
  let second: string;
  let dean: string;
  let owner: string;

  test.beforeAll(async ({ browser }) => {
    owner = await freshEmployee(browser, 'reviewowner');
    first = await freshEmployee(browser, 'reviewer1');
    second = await freshEmployee(browser, 'reviewer2');
    dean = await freshEmployee(browser, 'reviewdean');
    sql(`update users set organization_id = ${DEPARTMENT} where user_id = ${user(owner)}`);
    sql(`update user_roles set organization_id = ${DEPARTMENT} where user_id = ${user(owner)}`);
    grant(first, 'FACULTY_SECRETARY', 'Перша');
    grant(second, 'FACULTY_SECRETARY', 'Друга');
    grant(dean, 'DEAN', 'Деканова');
  });

  test.afterAll(() => {
    sql(
      `update award_requests set status = 'EXPIRED', completed_at = now() where award_id in (select award_id from awards where user_id = ${user(owner)})`,
    );
    sql(
      `delete from user_roles where organization_id = ${FACULTY} and user_id in (${user(first)}, ${user(second)}, ${user(dean)})`,
    );
  });

  test('ac1_10 ac1_11 two secretaries claim, conflict, hand over and a dean takes over', async ({
    browser,
  }) => {
    const title = `Грамота на розгляд ${uniqueToken()}`;
    const id = await submittedBy(browser, owner, title);
    const one = await signedInAs(browser, first, FRESH_PASSWORD);
    const two = await signedInAs(browser, second, FRESH_PASSWORD);

    await one.getByTestId('nav-reviews').click();
    await expect(one).toHaveURL(/\/reviews$/);
    await one.getByTestId('review-tab-unassigned').click();
    const row = one.getByTestId('review-item').filter({ hasText: title });
    await expect(row).toContainText('Ірина Нова');
    await expect(row.getByTestId('review-reviewer')).toHaveText('—');
    expect(await seriousViolations(one)).toEqual([]);
    await row.click();
    await expect(one).toHaveURL(new RegExp(`/awards/${id}$`));
    await expect(one.getByTestId('review-panel-reviewer')).toHaveText('Ще ніхто не взяв у роботу');

    await openAward(two, id);
    await one.getByTestId('review-claim').click();
    await expect(one.getByTestId('review-panel-reviewer')).toHaveText('Ірина Перша');
    expect(await seriousViolations(one)).toEqual([]);

    await two.getByTestId('review-claim').click();
    await expect(two.getByTestId('review-panel-notice')).toHaveText(
      'Нагороду вже взяв у роботу Ірина Перша',
    );
    await expect(two.getByTestId('review-panel-reviewer')).toHaveText('Ірина Перша');
    await expect(two.getByTestId('review-take-over')).toHaveCount(0);

    await one.getByTestId('review-hand-over').click();
    await one.getByRole('radio', { name: 'Ірина Друга' }).check();
    await one.getByTestId('hand-over-confirm').click();
    await expect(one.getByTestId('review-panel-reviewer')).toHaveText('Ірина Друга');

    await two.getByTestId('nav-reviews').click();
    await two.getByTestId('review-tab-me').click();
    await expect(two.getByTestId('review-item').filter({ hasText: title })).toBeVisible();

    const head = await signedInAs(browser, dean, FRESH_PASSWORD);
    await openAward(head, id);
    await head.getByTestId('review-take-over').click();
    await expect(head.getByRole('dialog')).toContainText('Ірина Друга');
    await head.getByTestId('confirm-accept').click();
    await expect(head.getByTestId('review-panel-reviewer')).toHaveText('Ірина Деканова');
    expect(
      sql(
        `select string_agg(action_type, ',' order by log_id) from audit_logs where entity_id = ${id} and action_type like 'REVIEW_%'`,
      ),
    ).toBe('REVIEW_CLAIMED,REVIEW_HANDED_OVER,REVIEW_TAKEN_OVER');
  });

  test('ac1_13 the queue and panel work in English at 360 px', async ({ browser }) => {
    const title = `Грамота для англійської ${uniqueToken()}`;
    const id = await submittedBy(browser, owner, title);
    const page = await signedInAs(browser, first, FRESH_PASSWORD);
    await page.setViewportSize({ width: 360, height: 780 });
    await page.getByTestId('language-toggle').click();

    await page.goto('/reviews');
    await expect(page.getByTestId('review-tab-unassigned')).toHaveText('Unassigned');
    await expect(page.getByTestId('review-table')).toHaveCount(0);
    const card = page
      .getByTestId('review-cards')
      .getByTestId('review-item')
      .filter({ hasText: title });
    await expect(card).toContainText('Faculty or department');
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(
      360,
    );
    expect(await seriousViolations(page)).toEqual([]);

    await card.getByTestId('review-title').click();
    await expect(page.getByTestId('review-panel')).toContainText('Nobody has claimed it yet');
    await page.getByTestId('review-claim').click();
    await expect(page.getByTestId('review-panel-notice')).toHaveText(
      'You are now reviewing this award.',
    );
    await page.getByTestId('review-release').click();
    await expect(page.getByTestId('review-panel-notice')).toHaveText(
      'The award is back in the queue.',
    );
    expect(await seriousViolations(page)).toEqual([]);
    expect(sql(`select status from award_requests where award_id = ${id}`)).toBe('SUBMITTED');
  });

  test('ac2_4 ac2_11 a returned award goes back to its owner with the comment', async ({
    browser,
  }) => {
    const title = `Грамота на доопрацювання ${uniqueToken()}`;
    const comment = 'Додайте номер і дату наказу';
    const id = await submittedBy(browser, owner, title, UNIVERSITY);
    const page = await signedInAs(browser, first, FRESH_PASSWORD);
    await openAward(page, id);

    await page.getByTestId('review-return').click();
    const dialog = page.getByRole('dialog');
    await expect(dialog.getByTestId('decision-title')).toHaveText('Повернути на доопрацювання');
    await dialog.getByTestId('decision-confirm').click();
    await expect(dialog).toContainText('Вкажіть, що потрібно виправити');
    await dialog.getByTestId('decision-comment').fill(comment);
    expect(await seriousViolations(page)).toEqual([]);
    await dialog.getByTestId('decision-confirm').click();

    await expect(page).toHaveURL(/\/reviews$/);
    await expect(page.getByTestId('reviews-notice')).toHaveText(
      'Нагороду повернуто на доопрацювання.',
    );
    const mine = await signedInAs(browser, owner, FRESH_PASSWORD);
    await mine.goto(`/awards/${id}`);
    await expect(mine.getByTestId('award-detail-status')).toHaveText('Чернетка');
    expect(sql(`select status from award_requests where award_id = ${id}`)).toBe('RETURNED');
    expect(
      sql(
        `select comments from review_decisions where request_id = (select request_id from award_requests where award_id = ${id})`,
      ),
    ).toBe(comment);

    await expect(mine.getByTestId('award-remove')).toHaveCount(0);
    await mine.getByTestId('award-edit').click();
    await expect(mine.getByTestId('award-returned')).toHaveText(
      `Рецензент повернув нагороду на доопрацювання: ${comment}`,
    );
    expect(await seriousViolations(mine)).toEqual([]);
    await submitWithoutDocuments(mine);
    await expect(mine.getByTestId('award-submitted-name')).toContainText(title);
    await mine.goto(`/awards/${id}`);
    await expect(mine.getByTestId('award-status-submitted')).toHaveText('Подано повторно');
    expect(sql(`select status || ' ' || current_level from award_requests where award_id = ${id}`)).toBe(
      'SUBMITTED FACULTY_SECRETARY',
    );
  });

  test('ac3_1 ac3_6 the owner withdraws an unclaimed award and edits it', async ({ browser }) => {
    const title = `Грамота для відкликання ${uniqueToken()}`;
    const id = await submittedBy(browser, owner, title, UNIVERSITY);
    const mine = await signedInAs(browser, owner, FRESH_PASSWORD);
    await mine.goto(`/awards/${id}`);

    await mine.getByTestId('award-withdraw').click();
    await expect(mine.getByRole('dialog')).toContainText('Вона знову стане чернеткою.');
    expect(await seriousViolations(mine)).toEqual([]);
    await mine.getByTestId('confirm-accept').click();

    await expect(mine).toHaveURL(new RegExp(`/awards/${id}/edit$`));
    await expect(mine.getByTestId('award-title-uk')).toHaveValue(title);
    await expect(mine.getByTestId('award-returned')).toHaveCount(0);
    expect(sql(`select status from award_requests where award_id = ${id}`)).toBe('WITHDRAWN');
    expect(
      sql(`select count(*) from audit_logs where entity_id = ${id} and action_type = 'AWARD_WITHDRAWN'`),
    ).toBe('1');
  });

  test('ac3_2 ac3_6 a claimed award cannot be withdrawn', async ({ browser }) => {
    const title = `Грамота, взята в роботу ${uniqueToken()}`;
    const id = await submittedBy(browser, owner, title, UNIVERSITY);
    const mine = await signedInAs(browser, owner, FRESH_PASSWORD);
    await mine.goto(`/awards/${id}`);
    await expect(mine.getByTestId('award-withdraw')).toBeVisible();

    sql(
      `update award_requests set status = 'IN_REVIEW', current_reviewer_id = ${user(first)} where award_id = ${id}`,
    );
    await mine.getByTestId('award-withdraw').click();
    await mine.getByTestId('confirm-accept').click();

    await expect(mine.getByTestId('award-detail-notice')).toHaveText(
      'Нагороду вже розглядає рецензент, відкликати її не можна.',
    );
    await expect(mine.getByTestId('award-withdraw')).toHaveCount(0);
    expect(sql(`select status from award_requests where award_id = ${id}`)).toBe('IN_REVIEW');
  });

  test('ac2_2 ac2_5 ac2_11 a secretary passes a national award on and a dean approves another', async ({
    browser,
  }) => {
    const national = await submittedBy(browser, owner, `Відзнака МОН ${uniqueToken()}`);
    const university = await submittedBy(
      browser,
      owner,
      `Університетська грамота ${uniqueToken()}`,
      UNIVERSITY,
    );
    const page = await signedInAs(browser, first, FRESH_PASSWORD);
    await openAward(page, national);

    await page.getByTestId('review-escalate').click();
    await expect(page.getByRole('dialog').getByTestId('decision-title')).toHaveText(
      'Передати декану',
    );
    await page.getByTestId('decision-comment').fill('Національний рівень');
    await page.getByTestId('decision-confirm').click();
    await expect(page.getByTestId('award-detail-decision')).toHaveText('Передано декану.');
    await expect(page.getByTestId('award-status-decision')).toContainText('Ірина Перша');
    await expect(page.getByTestId('award-status-comment')).toHaveText('Національний рівень');
    expect(
      sql(`select status || ':' || current_level from award_requests where award_id = ${national}`),
    ).toBe('ESCALATED:DEAN');

    const head = await signedInAs(browser, dean, FRESH_PASSWORD);
    await openAward(head, university);
    await head.getByTestId('review-approve').click();
    await expect(head.getByTestId('decision-verified')).toHaveCount(0);
    await head.getByTestId('decision-confirm').click();
    await expect(head.getByTestId('award-detail-decision')).toHaveText('Нагороду затверджено.');
    await expect(head.getByTestId('review-panel')).toHaveCount(0);
    await expect(head.getByTestId('award-status-completed')).toBeVisible();
    expect(await seriousViolations(head)).toEqual([]);
    expect(sql(`select status from awards where award_id = ${university}`)).toBe('APPROVED');
  });
});
