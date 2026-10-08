import { expect, test } from '@playwright/test';

import { FRESH_PASSWORD, freshEmployee, seriousViolations, signedInAs, sql } from './helpers';

/** A faculty without seed reviewers or awards, so changing its period touches no other spec. */
const FACULTY = 12;

function grant(email: string, role: string): void {
  sql(
    `insert into user_roles (user_id, organization_id, role_type) select user_id, ${FACULTY}, '${role}' from users where email_address = '${email}'`,
  );
}

function storedPeriod(): string {
  return sql(
    `select coalesce(review_working_days::text, 'default') from organizations where org_id = ${FACULTY}`,
  );
}

test.describe('faculty review period', () => {
  test.describe.configure({ mode: 'serial' });

  let dean: string;
  let secretary: string;

  test.beforeAll(async ({ browser }) => {
    sql(`update organizations set review_working_days = null where org_id = ${FACULTY}`);
    dean = await freshEmployee(browser, 'period.dean');
    secretary = await freshEmployee(browser, 'period.secretary');
    grant(dean, 'DEAN');
    grant(secretary, 'FACULTY_SECRETARY');
  });

  test.afterAll(() => {
    sql(`update organizations set review_working_days = null where org_id = ${FACULTY}`);
  });

  test('ac1_1_ac1_2_ac1_3_ac1_4 the dean sets the period, the secretary sees it, the default returns', async ({
    browser,
  }) => {
    const page = await signedInAs(browser, dean, FRESH_PASSWORD);
    await page.goto('/reviews');
    const text = page.getByTestId('review-period-text');
    await expect(text).toHaveText('Термін розгляду: 3 робочі дні (типовий)');

    await page.getByTestId('review-period-change').click();
    const input = page.getByTestId('review-period-input');
    await input.fill('0');
    await page.getByTestId('review-period-save').click();
    await expect(page.getByTestId('review-period-range')).toHaveText(
      'Вкажіть ціле число від 1 до 20',
    );
    expect(storedPeriod()).toBe('default');

    await input.fill('5');
    await page.getByTestId('review-period-save').click();
    await expect(text).toHaveText('Термін розгляду: 5 робочих днів');
    expect(storedPeriod()).toBe('5');
    expect(
      sql(
        `select count(*) from audit_logs where action_type = 'REVIEW_PERIOD_CHANGED' and entity_id = ${FACULTY} and user_id = (select user_id from users where email_address = '${dean}')`,
      ),
    ).toBe('1');

    const other = await signedInAs(browser, secretary, FRESH_PASSWORD);
    await other.goto('/reviews');
    await expect(other.getByTestId('review-period-text')).toHaveText(
      'Термін розгляду: 5 робочих днів',
    );
    await expect(other.getByTestId('review-period-change')).toHaveCount(0);
    await other.context().close();

    await page.getByTestId('review-period-change').click();
    await page.getByTestId('review-period-default').click();
    await expect(text).toHaveText('Термін розгляду: 3 робочі дні (типовий)');
    expect(storedPeriod()).toBe('default');
    await page.context().close();
  });

  test('ac1_9 the header and dialog work in English at 360 px by keyboard', async ({ browser }) => {
    const page = await signedInAs(browser, dean, FRESH_PASSWORD);
    await page.setViewportSize({ width: 360, height: 780 });
    await page.getByTestId('language-toggle').click();
    await page.goto('/reviews');

    const text = page.getByTestId('review-period-text');
    await expect(text).toHaveText('Review period: 3 working days (default)');
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(
      360,
    );
    expect(await seriousViolations(page)).toEqual([]);

    await page.getByTestId('review-period-change').focus();
    await page.keyboard.press('Enter');
    const input = page.getByTestId('review-period-input');
    await expect(input).toBeFocused();
    await input.fill('7');
    expect(await seriousViolations(page)).toEqual([]);
    await page.keyboard.press('Enter');

    await expect(text).toHaveText('Review period: 7 working days');
    expect(storedPeriod()).toBe('7');
    await page.context().close();
  });
});
