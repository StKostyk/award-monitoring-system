import { Browser, Page, expect, test } from './fixtures';
import {
  FRESH_PASSWORD,
  SEED,
  countMessages,
  freshEmployee,
  openNewAward,
  pastDay,
  seriousViolations,
  signedIn,
  signedInAs,
  sql,
  submitWithoutDocuments,
  uniqueToken,
} from './helpers';

/** A faculty without seed reviewers, with departments that have no award: one for the queue, one to remind. */
const FACULTY = 3;
const QUEUE_DEPARTMENT = 35;
const REMINDED_DEPARTMENT = 36;
/** A university category: the faculty secretary's approval is final. */
const UNIVERSITY = 21;
const HOLD = 10_000;
/** `APP_REMINDERS_UNIT_INTERVAL` of the e2e backend (`tools/e2e.ps1`), with a margin. */
const REMINDER_INTERVAL = 90_000;
const MIN_TARGET = 44;
const SUBJECT = 'Нагадування про нагороди';
const API = 'http://localhost:8080/api/v1';

function userId(email: string): string {
  return sql(`select user_id from users where email_address = '${email}'`);
}

/** A fresh account moved into a department of the faculty. */
async function freshMember(browser: Browser, name: string, department: number): Promise<string> {
  const email = await freshEmployee(browser, name);
  sql(`update users set organization_id = ${department} where user_id = ${userId(email)}`);
  sql(`update user_roles set organization_id = ${department} where user_id = ${userId(email)}`);
  return email;
}

/** A fresh account with its own faculty secretary role over the faculty. */
async function freshSecretary(browser: Browser): Promise<string> {
  const email = await freshEmployee(browser, 'centre-secretary');
  sql(
    `insert into user_roles (user_id, organization_id, role_type) values (${userId(email)}, ${FACULTY}, 'FACULTY_SECRETARY')`,
  );
  return email;
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

async function figure(page: Page, testId: string): Promise<number> {
  const text = await page.getByTestId(testId).locator('.today__value').innerText();
  return Number(text);
}

async function confirmDecision(page: Page): Promise<void> {
  const dialog = page.getByRole('dialog');
  await expect(dialog.getByTestId('decision-title')).toBeVisible();
  await dialog.getByTestId('decision-confirm').click();
  await expect(dialog).toHaveCount(0);
}

test.describe('command centre', () => {
  /** The colleague to remind, under a name no other account has, in place before any page reads the faculty. */
  let member = '';
  let lastName = '';

  test.beforeAll(async ({ browser }) => {
    member = await freshMember(browser, 'centre-member', REMINDED_DEPARTMENT);
    lastName = `Нагадування${uniqueToken()}`;
    sql(`update users set last_name = '${lastName}' where user_id = ${userId(member)}`);
  });

  test('ac5_1 ac5_7 a secretary approves from the queue, undoes, and the count follows', async ({
    browser,
  }) => {
    const owner = await freshMember(browser, 'centre-owner', QUEUE_DEPARTMENT);
    const secretary = await freshSecretary(browser);
    const title = `Грамота командного центру ${uniqueToken()}`;
    const id = await submittedBy(browser, owner, title);
    const page = await signedInAs(browser, secretary, FRESH_PASSWORD);

    await page.goto('/');
    await expect(page).toHaveURL(/\/command-centre$/);
    await expect(page).toHaveTitle(/Командний центр/);
    const item = page.getByTestId('queue-item').filter({ hasText: title });
    await expect(item).toHaveCount(1);
    await expect(page.getByTestId('today-decided')).toContainText('0');
    const waiting = await figure(page, 'today-waiting');
    expect(waiting).toBeGreaterThanOrEqual(1);

    await item.getByTestId('queue-approve').click();
    await confirmDecision(page);
    const snackBar = page.locator('mat-snack-bar-container');
    await expect(snackBar).toContainText('Рішення збережено');
    await expect(item).toHaveCount(0);
    await expect.poll(() => figure(page, 'today-waiting')).toBe(waiting - 1);
    await snackBar.getByRole('button', { name: 'Скасувати' }).click();
    await expect(item).toHaveCount(1);
    await page.waitForTimeout(HOLD + 1000);
    expect(decisions(id)).toBe(0);

    await item.getByTestId('queue-approve').click();
    await confirmDecision(page);
    await expect.poll(() => decisions(id), { timeout: HOLD + 8000 }).toBe(1);
    await expect(page.getByTestId('today-decided')).toContainText('1');
    await expect.poll(() => figure(page, 'today-waiting')).toBeLessThan(waiting);
    expect(sql(`select status from awards where award_id = ${id}`)).toBe('APPROVED');
    await page.context().close();
  });

  test('ac5_2 ac5_3 ac5_4 a secretary reminds a department without awards once', async ({
    browser,
  }) => {
    const secretary = await freshSecretary(browser);
    const page = await signedInAs(browser, secretary, FRESH_PASSWORD);
    const card = page.getByTestId(`focus-unit-${REMINDED_DEPARTMENT}`);
    const remind = card.getByRole('button');

    await expect(async () => {
      await page.goto('/command-centre');
      await expect(remind).toHaveText('Нагадати завідувачу', { timeout: 5000 });
    }).toPass({ timeout: REMINDER_INTERVAL });
    await expect(card).toContainText(`Жодної нагороди у ${new Date().getFullYear()}`);

    await remind.click();
    const dialog = page.getByRole('dialog');
    await dialog.getByTestId('reminder-recipient').click();
    await page.getByRole('option', { name: new RegExp(`^${lastName} `) }).click();
    const sent = page.waitForResponse((response) => response.url().endsWith('/reminders'));
    await dialog.getByTestId('reminder-send').click();
    expect((await sent).status()).toBe(202);
    await expect(dialog).toHaveCount(0);
    await expect(remind).toBeDisabled();
    await expect(remind).toContainText('Нагадано');
    await expect.poll(() => countMessages(member, SUBJECT), { timeout: 20_000 }).toBe(1);

    const token = await page.evaluate(() => sessionStorage.getItem('access_token'));
    const again = await page.request.post(
      `${API}/analytics/units/${REMINDED_DEPARTMENT}/reminders`,
      {
        headers: { Authorization: `Bearer ${token}` },
        data: { recipientId: Number(userId(member)) },
      },
    );
    expect(again.status()).toBe(409);
    expect((await again.json()).type).toBe('urn:awards:problem:recently-reminded');

    await page.goBack();
    await page.goForward();
    await expect(remind).toBeDisabled();
    expect(await countMessages(member, SUBJECT)).toBe(1);
    await page.context().close();
  });

  test('ac5_6 a rector secretary sees the faculties without a reminder', async ({ browser }) => {
    const page = await signedIn(browser, SEED.rectorSecretary);

    await page.goto('/');
    await expect(page).toHaveURL(/\/command-centre$/);
    await expect(page.getByTestId('centre-units')).toContainText('Факультети, що потребують уваги');
    await expect(page.getByTestId('centre-units').getByRole('button')).toHaveCount(0);
    await page.context().close();
  });

  test('ac5_7 the queue buttons keep 44 px at 360 px and the page passes axe', async ({
    browser,
  }) => {
    const owner = await freshMember(browser, 'centre-narrow', QUEUE_DEPARTMENT);
    const secretary = await freshSecretary(browser);
    const title = `Грамота вузького екрана ${uniqueToken()}`;
    await submittedBy(browser, owner, title);
    const page = await signedInAs(browser, secretary, FRESH_PASSWORD);
    await page.setViewportSize({ width: 360, height: 800 });

    await page.goto('/command-centre');
    const item = page.getByTestId('queue-item').filter({ hasText: title });
    await expect(item).toHaveCount(1);
    for (const testId of ['queue-approve', 'queue-return']) {
      const box = await item.getByTestId(testId).boundingBox();
      expect(box?.height ?? 0).toBeGreaterThanOrEqual(MIN_TARGET);
    }
    expect(
      await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
    ).toBe(true);
    expect(await seriousViolations(page)).toEqual([]);
    await page.context().close();
  });
});
