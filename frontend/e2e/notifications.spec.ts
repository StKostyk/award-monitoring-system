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

/** A faculty without seed reviewers, so the decision below is the only one its dean sees. */
const FACULTY = 5;
const DEPARTMENT = 41;
/** A university category: any reviewing level approves it for good. */
const UNIVERSITY = 21;

function user(email: string): string {
  return `(select user_id from users where email_address = '${email}')`;
}

function notify(email: string, type: string, payload: Record<string, unknown>): void {
  sql(
    `insert into notifications (user_id, type, payload, dedupe_key) values (${user(email)}, '${type}', ` +
      `'${JSON.stringify(payload).replaceAll("'", "''")}'::jsonb, md5(random()::text))`,
  );
}

async function submitted(page: Page, title: string): Promise<string> {
  await page.goto('/awards/new');
  await page.getByTestId('award-title-uk').fill(title);
  await page.getByTestId('award-category').click();
  await page.getByTestId(`category-option-${UNIVERSITY}`).click();
  await page.getByTestId('award-organization').fill('Міністерство освіти і науки України');
  await page.getByTestId('award-date').fill(pastDay());
  await submitWithoutDocuments(page);
  await expect(page.getByTestId('award-submitted-name')).toContainText(title);
  return sql(`select award_id from awards where title_uk = '${title}'`);
}

async function approvedBy(browser: Browser, dean: string, id: string): Promise<void> {
  const page = await signedInAs(browser, dean, FRESH_PASSWORD);
  const reviewer = page.waitForResponse((response) =>
    response.url().endsWith(`/awards/${id}/reviewer`),
  );
  await page.goto(`/awards/${id}`);
  await reviewer;
  await page.getByTestId('review-approve').click();
  await page.getByTestId('decision-confirm').click();
  await expect(page.getByTestId('award-detail-decision')).toHaveText('Нагороду затверджено.');
  await page.context().close();
}

test.describe('notification centre', () => {
  test('ac2_1 ac2_3 ac2_4 ac2_7 a decision shows in the bell and leads to the award', async ({
    browser,
  }) => {
    const owner = await freshEmployee(browser, 'bellowner');
    const dean = await freshEmployee(browser, 'belldean');
    sql(`update users set organization_id = ${DEPARTMENT} where user_id = ${user(owner)}`);
    sql(`update user_roles set organization_id = ${DEPARTMENT} where user_id = ${user(owner)}`);
    sql(
      `insert into user_roles (user_id, organization_id, role_type) values (${user(dean)}, ${FACULTY}, 'DEAN')`,
    );
    sql(
      `update users set first_name = 'Олена', last_name = 'Деканова' where user_id = ${user(dean)}`,
    );
    const page = await signedInAs(browser, owner, FRESH_PASSWORD);
    const title = `Грамота зі сповіщенням ${uniqueToken()}`;
    const id = await submitted(page, title);
    const bell = page.getByTestId('notification-bell');
    await expect(bell).toHaveAccessibleName('Сповіщення');

    await approvedBy(browser, dean, id);
    await expect
      .poll(() => sql(`select count(*) from notifications where user_id = ${user(owner)}`))
      .toBe('1');
    await page.goto('/achievements');
    await expect(bell).toHaveAccessibleName('Сповіщення, непрочитаних: 1');
    await expect(page.getByTestId('notification-badge')).toContainText('1');

    await bell.click();
    const item = page.getByTestId('notification-item');
    await expect(item).toHaveCount(1);
    await expect(item).toContainText(
      `«${title}» схвалено (рівень: секретар факультету): Олена Деканова`,
    );
    expect(await seriousViolations(page)).toEqual([]);
    await page.keyboard.press('Escape');
    await page.getByTestId('language-toggle').click();
    await bell.click();
    await expect(item).toContainText(
      `«${title}» approved by Олена Деканова at the faculty secretary level`,
    );

    await item.click();
    await expect(page).toHaveURL(new RegExp(`/awards/${id}$`));
    await expect(bell).toHaveAccessibleName('Notifications');
    expect(sql(`select is_read from notifications where user_id = ${user(owner)}`)).toBe('t');
  });

  test('ac2_5 ac2_6 the page filters unread and mark all empties the badge', async ({
    browser,
  }) => {
    const owner = await freshEmployee(browser, 'bellpage');
    const role = {
      role: 'DEAN',
      organization: 'Faculty of Mathematics',
      organizationUk: 'Факультет математики',
      validTo: null,
    };
    notify(owner, 'ROLE_ASSIGNED', role);
    notify(owner, 'REVIEW_OVERDUE', { count: 2, oldestDeadline: '2026-10-01' });
    notify(owner, 'DELEGATION_CREATED', { ...role, otherParty: 'Мартин Мартинюк' });
    sql(
      `update notifications set is_read = true, read_at = now() where user_id = ${user(owner)} and type = 'ROLE_ASSIGNED'`,
    );
    const page = await signedInAs(browser, owner, FRESH_PASSWORD);

    await page.goto('/notifications');
    const rows = page.getByTestId('notifications-list-item');
    await expect(rows).toHaveCount(3);
    await expect(page.getByTestId('notification-bell')).toHaveAccessibleName(
      'Сповіщення, непрочитаних: 2',
    );
    expect(await seriousViolations(page)).toEqual([]);

    await page.getByTestId('notifications-unread-only').locator('button').click();
    await expect(page).toHaveURL(/\/notifications\?unread=true$/);
    await expect(rows).toHaveCount(2);

    await page.getByTestId('notification-bell').click();
    await page.getByTestId('notifications-mark-all').click();
    await expect(page.getByTestId('notification-bell')).toHaveAccessibleName('Сповіщення');
    await page.reload();
    await expect(page.getByTestId('notifications-page-empty')).toHaveText(
      'Непрочитаних сповіщень немає',
    );
    await expect(page.getByTestId('notifications-page-mark-all')).toBeDisabled();

    await page.getByTestId('notifications-unread-only').locator('button').click();
    await expect(page).toHaveURL(/\/notifications$/);
    await rows.filter({ hasText: 'доручає вам роль' }).click();
    await expect(page).toHaveURL(/\/(delegations|forbidden)$/);
  });

  for (const scheme of ['light', 'dark'] as const) {
    test(`ac2_7 the menu and the page pass the screen check at 360 px in ${scheme}`, async ({
      browser,
    }) => {
      const owner = await freshEmployee(browser, `bell${scheme}`);
      notify(owner, 'AWARD_RETURNED', {
        title: 'A very long award title that needs two lines in the menu at the phone width',
        titleUk: 'Дуже довга назва нагороди, якій у меню на вузькому екрані потрібні два рядки',
        reviewer: 'Аліна Мартинюк',
        level: 'FACULTY_SECRETARY',
      });
      const page = await signedInAs(browser, owner, FRESH_PASSWORD);
      await page.setViewportSize({ width: 360, height: 740 });
      await page.emulateMedia({ colorScheme: scheme });

      await page.goto('/notifications');
      await expect(page.getByTestId('notifications-list-item')).toHaveCount(1);
      expect(await seriousViolations(page)).toEqual([]);
      expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(
        360,
      );

      await page.getByTestId('notification-bell').click();
      await expect(page.getByTestId('notification-item')).toHaveCount(1);
      expect(await seriousViolations(page)).toEqual([]);
    });
  }
});
