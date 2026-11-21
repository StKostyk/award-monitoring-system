import { Browser, Page, expect, test } from './fixtures';

import {
  FRESH_PASSWORD,
  SEED,
  expireOpenRequests,
  freshEmployee,
  openNewAward,
  pastDay,
  seriousViolations,
  signedIn,
  signedInAs,
  sql,
  submitWithoutDocuments,
  uniqueToken,
  userRow,
} from './helpers';

/** The faculty of the reviewer queue specs, without seed reviewers. */
const FACULTY = 5;
const DEPARTMENT = 41;
/** The faculty of the seed dean, who may revoke its secretary role, and one of its departments. */
const DEAN_FACULTY = 9;
const DEAN_DEPARTMENT = 64;
/** A university category: the faculty secretary's approval is final. */
const UNIVERSITY = 21;
const HOLD = 10_000;

function user(email: string): string {
  return `(select user_id from users where email_address = '${email}')`;
}

function decisions(id: string): number {
  return Number(
    sql(
      `select count(*) from review_decisions d join award_requests r on r.request_id = d.request_id
       where r.award_id = ${id}`,
    ),
  );
}

async function submittedBy(browser: Browser, owner: string, title: string): Promise<string> {
  const page = await signedInAs(browser, owner, FRESH_PASSWORD);
  await openNewAward(page);
  await page.getByTestId('award-title-uk').fill(title);
  await page.getByTestId('award-category').click();
  await page.getByTestId(`category-option-${UNIVERSITY}`).click();
  await page.getByTestId('award-organization').fill('Міністерство освіти і науки України');
  await page.getByTestId('award-date').fill(pastDay());
  await submitWithoutDocuments(page);
  await expect(page.getByTestId('award-submitted-name')).toContainText(title);
  await page.context().close();
  return sql(`select award_id from awards where title_uk = '${title}'`);
}

/**
 * Opens the queue filtered to the owner's department and moves with J, page by page, until the request with the
 * title is selected; the queue lists the oldest deadline first, so a new request is on the last page.
 */
async function selectInQueue(page: Page, title: string, department = DEPARTMENT): Promise<void> {
  await page.goto('/reviews');
  await page.getByTestId('review-filter-unit').click();
  const unit = sql(`select name_uk from organizations where org_id = ${department}`);
  const filtered = page.waitForResponse((response) =>
    response.url().includes(`organizationId=${department}`),
  );
  await page.getByRole('option', { name: unit, exact: true }).click();
  await filtered;
  await expect(page.getByTestId('reviews-loading')).toHaveCount(0);
  const open = page.getByTestId('queue-open');
  const next = page.locator('.mat-mdc-paginator-navigation-next');
  for (;;) {
    await expect(open).toBeVisible();
    const count = await page.getByTestId('review-item').count();
    for (let step = 0; step < count && (await open.textContent())?.trim() !== title; step++) {
      await page.locator('main h1').click();
      await page.keyboard.press('j');
    }
    if ((await open.textContent())?.trim() === title || !(await next.isEnabled())) {
      break;
    }
    await next.click();
    await expect(page.getByTestId('reviews-loading')).toHaveCount(0);
  }
  await expect(open).toHaveText(title);
}

async function approveWithKeyboard(page: Page): Promise<void> {
  await page.keyboard.press('a');
  const dialog = page.getByRole('dialog');
  await expect(dialog.getByTestId('decision-title')).toBeVisible();
  await dialog.getByTestId('decision-confirm').click();
  await expect(dialog).toHaveCount(0);
}

test.describe('review queue with checklist, shortcuts and undo', () => {
  let owner: string;
  let reviewer: string;

  test.beforeAll(async ({ browser }) => {
    owner = await freshEmployee(browser, 'queueowner');
    reviewer = await freshEmployee(browser, 'queuereviewer');
    sql(`update users set organization_id = ${DEPARTMENT} where user_id = ${user(owner)}`);
    sql(`update user_roles set organization_id = ${DEPARTMENT} where user_id = ${user(owner)}`);
    sql(
      `insert into user_roles (user_id, organization_id, role_type) select user_id, ${FACULTY}, 'FACULTY_SECRETARY' from users where email_address = '${reviewer}'`,
    );
  });

  test.afterAll(() => {
    expireOpenRequests(`u.email_address = '${owner}'`);
    sql(
      `delete from user_roles where organization_id = ${FACULTY} and user_id = ${user(reviewer)}`,
    );
  });

  test('ac6_1 to ac6_4 a request is checked, approved with A, undone, and sent after the hold', async ({
    browser,
  }) => {
    const title = `Грамота з черги ${uniqueToken()}`;
    const id = await submittedBy(browser, owner, title);
    const page = await signedInAs(browser, reviewer, FRESH_PASSWORD);

    await selectInQueue(page, title);
    await expect(page.getByTestId('queue-left')).toContainText(/Залишилось: \d+/);
    await expect(page.getByTestId('queue-checklist')).toContainText('Документ читається');
    await expect(page.getByTestId('queue-shortcuts')).toContainText('J / K');
    await page.getByTestId('queue-check-readable').click();
    expect(await seriousViolations(page)).toEqual([]);

    await page.getByTestId('queue-check-readable').locator('input').focus();
    await approveWithKeyboard(page);
    const snackBar = page.locator('mat-snack-bar-container');
    await expect(snackBar).toContainText('Рішення збережено');
    await expect(page.getByTestId('review-item').filter({ hasText: title })).toHaveCount(0);
    expect(decisions(id)).toBe(0);
    await snackBar.getByRole('button', { name: 'Скасувати' }).click();
    await expect(page.getByTestId('review-item').filter({ hasText: title })).toHaveCount(1);
    await page.waitForTimeout(HOLD + 1000);
    expect(decisions(id)).toBe(0);

    await selectInQueue(page, title);
    await page.locator('main h1').click();
    await approveWithKeyboard(page);
    await page.waitForTimeout(HOLD - 2000);
    expect(decisions(id)).toBe(0);
    await expect.poll(() => decisions(id), { timeout: 8000 }).toBe(1);
    expect(sql(`select status from awards where award_id = ${id}`)).toBe('APPROVED');

    await page.goto(`/reviews?request=${id}`);
    await expect(page.getByTestId('queue-not-waiting')).toBeVisible();
    await expect(page.getByTestId('queue-state')).toHaveText('Затверджено');
    await expect(page.getByTestId('queue-approve')).toHaveCount(0);
    await page.context().close();
  });

  test('ac5_1 ac5_2 the pane keeps the selection in the url and hands the request over', async ({
    browser,
  }) => {
    const title = `Лист з черги ${uniqueToken()}`;
    const id = await submittedBy(browser, owner, title);
    const page = await signedInAs(browser, reviewer, FRESH_PASSWORD);

    await page.goto(`/reviews?request=${id}`);
    await expect(page.getByTestId('queue-open')).toHaveText(title);
    await expect(page.getByTestId('queue-level')).toHaveText('Секретар факультету');
    await expect(page.getByTestId('award-status-panel')).toBeVisible();
    await expect(page.getByTestId('queue-escalate')).toBeVisible();
    const rows = await page.getByTestId('review-rows').boundingBox();
    const pane = await page.getByTestId('review-queue').boundingBox();
    expect(pane?.x ?? 0).toBeGreaterThan((rows?.x ?? 0) + (rows?.width ?? 0));
    await page.reload();
    await expect(page.getByTestId('queue-open')).toHaveText(title);
    expect(await seriousViolations(page)).toEqual([]);

    await page.getByTestId('queue-hand-over').click();
    await expect(page.getByRole('dialog')).toBeVisible();
    await page.keyboard.press('Escape');
    await expect(page.getByRole('dialog')).toHaveCount(0);
    expect(
      sql(
        `select count(*) from award_requests where award_id = ${id} and current_reviewer_id is not null`,
      ),
    ).toBe('0');
    await page.context().close();
  });

  test('edge a request closed elsewhere leaves the selection on the next refresh with a snack bar', async ({
    browser,
  }) => {
    const title = `Відзнака з черги ${uniqueToken()}`;
    const id = await submittedBy(browser, owner, title);
    const page = await signedInAs(browser, reviewer, FRESH_PASSWORD);

    await selectInQueue(page, title);
    await page
      .getByTestId('review-item')
      .filter({ hasText: title })
      .getByTestId('review-title')
      .click();
    await expect(page).toHaveURL(new RegExp(`request=${id}`));
    expireOpenRequests(`a.award_id = ${id}`);

    await page.getByTestId('review-tab-me').click();

    await expect(page.locator('mat-snack-bar-container')).toContainText(
      'Цього запиту більше немає в черзі.',
    );
    await expect(page).not.toHaveURL(/request=/);
    await expect(page.getByTestId('review-item').filter({ hasText: title })).toHaveCount(0);
    await page.context().close();
  });

  test('ac6_5 moving to another page sends the held decision at once', async ({ browser }) => {
    const title = `Подяка з черги ${uniqueToken()}`;
    const id = await submittedBy(browser, owner, title);
    const page = await signedInAs(browser, reviewer, FRESH_PASSWORD);

    await selectInQueue(page, title);
    await page.locator('main h1').click();
    await approveWithKeyboard(page);
    await page.getByTestId('nav-awards').click();

    await expect.poll(() => decisions(id), { timeout: 4000 }).toBe(1);
    await page.context().close();
  });

  test('ac6_6 a request changed during the hold comes back with the message', async ({
    browser,
  }) => {
    const title = `Диплом з черги ${uniqueToken()}`;
    const id = await submittedBy(browser, owner, title);
    const page = await signedInAs(browser, reviewer, FRESH_PASSWORD);

    await selectInQueue(page, title);
    await page.locator('main h1').click();
    await approveWithKeyboard(page);
    sql(`update award_requests set version = version + 1 where award_id = ${id}`);

    await expect(page.locator('mat-snack-bar-container')).not.toContainText('Рішення збережено', {
      timeout: HOLD + 5000,
    });
    await expect(page.getByTestId('review-item').filter({ hasText: title })).toHaveCount(1);
    expect(decisions(id)).toBe(0);
    await page.context().close();
  });
});

test.describe('held decision when the role is revoked during the hold', () => {
  let owner: string;
  let reviewer: string;

  test.beforeAll(async ({ browser }) => {
    owner = await freshEmployee(browser, 'revokedowner');
    reviewer = await freshEmployee(browser, 'revokedreviewer');
    sql(`update users set organization_id = ${DEAN_DEPARTMENT} where user_id = ${user(owner)}`);
    sql(
      `insert into user_roles (user_id, organization_id, role_type) select user_id, ${DEAN_FACULTY}, 'FACULTY_SECRETARY' from users where email_address = '${reviewer}'`,
    );
  });

  test.afterAll(() => {
    expireOpenRequests(`u.email_address = '${owner}'`);
  });

  test('ac1 the decision lost with the session is named after the next sign-in', async ({
    browser,
  }) => {
    const title = `Грамота відкликаного ${uniqueToken()}`;
    const id = await submittedBy(browser, owner, title);
    const page = await signedInAs(browser, reviewer, FRESH_PASSWORD);
    const dean = await signedIn(browser, SEED.dean);
    await dean.goto('/admin/users');
    await dean.getByTestId('filter-q').fill(reviewer);
    await userRow(dean, reviewer).click();
    await dean
      .getByRole('listitem')
      .filter({ hasText: 'Секретар факультету' })
      .getByTestId('revoke-role')
      .click();
    await expect(dean.getByTestId('revoke-text')).toContainText('Секретар факультету');

    await selectInQueue(page, title, DEAN_DEPARTMENT);
    await page.locator('main h1').click();
    await approveWithKeyboard(page);
    await dean.getByTestId('revoke-confirm').click();
    await expect(dean.getByTestId('detail-message')).toContainText('Роль відкликано');

    await expect(page).toHaveURL(/localhost:8080\/login/, { timeout: HOLD + 10_000 });
    expect(decisions(id)).toBe(0);
    await page.fill('#username', reviewer);
    await page.fill('#password', FRESH_PASSWORD);
    await page.click('button[type="submit"]');

    await expect(page.locator('mat-snack-bar-container')).toContainText(
      `Рішення не збережено, нагороди: №${id}`,
    );
    await dean.context().close();
    await page.context().close();
  });
});
