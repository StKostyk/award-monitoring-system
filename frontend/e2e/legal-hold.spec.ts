import { Page, expect, test } from './fixtures';

import {
  DEMO_PASSWORD,
  FRESH_PASSWORD,
  SEED,
  registerAndVerify,
  seriousViolations,
  signIn,
  sql,
} from './helpers';

const { gdpr, dean } = SEED;
const PLACE_REASON = 'Inspection 2026/14 by the ministry';
const LIFT_REASON = 'Inspection closed without findings';

async function openUser(page: Page, email: string): Promise<void> {
  await page.goto('/admin/users');
  await page.getByTestId('filter-q').fill(email);
  await page.getByRole('row').filter({ hasText: email }).getByTestId('user-link').click();
  await expect(page.getByTestId('detail-email')).toHaveText(email);
}

function audited(action: string, email: string): string {
  return sql(
    `select new_values::text from audit_logs where action_type = '${action}' and entity_id = ` +
      `(select user_id from users where email_address = '${email}') order by log_id desc limit 1`,
  );
}

test.describe('legal hold', () => {
  test('ac3_2 ac3_3 ac3_6 the GDPR officer places and lifts a hold with a reason', async ({
    page,
  }) => {
    const email = `held.${Date.now()}@chnu.edu.ua`;
    await registerAndVerify(page, email, FRESH_PASSWORD);
    await signIn(page, gdpr, DEMO_PASSWORD);
    await openUser(page, email);
    const card = page.getByTestId('legal-hold-card');

    await expect(card.getByTestId('legal-hold-none')).toHaveText('Утримання немає');

    await card.getByTestId('legal-hold-place').click();
    await page.getByTestId('hold-reason').fill('too short');
    await page.getByTestId('hold-dialog-confirm').click();

    await expect(page.getByRole('dialog')).toContainText('Щонайменше 10 символів');
    expect(await seriousViolations(page)).toEqual([]);

    await page.getByTestId('hold-reason').fill(PLACE_REASON);
    await page.getByTestId('hold-dialog-confirm').click();

    await expect(card.getByTestId('legal-hold-active')).toContainText(PLACE_REASON);
    await expect(card.getByTestId('legal-hold-message')).toHaveText(
      'Юридичне утримання встановлено.',
    );
    await expect(card.getByTestId('legal-hold-place')).toHaveCount(0);
    expect(await seriousViolations(page)).toEqual([]);
    const placed = audited('LEGAL_HOLD_PLACED', email);
    expect(placed).toContain(`"reasonLength": ${PLACE_REASON.length}`);
    expect(placed).not.toContain('Inspection');

    await card.getByTestId('legal-hold-lift').click();
    await page.getByTestId('hold-reason').fill(LIFT_REASON);
    await page.getByTestId('hold-dialog-confirm').click();

    await expect(card.getByTestId('legal-hold-none')).toBeVisible();
    await expect(card.getByTestId('legal-hold-history')).toContainText(LIFT_REASON);
    await expect(card.getByTestId('legal-hold-place')).toBeVisible();
    expect(audited('LEGAL_HOLD_LIFTED', email)).not.toBe('');
  });

  test('ac3_6 the dean sees the user without a legal hold card', async ({ page }) => {
    const email = `unheld.${Date.now()}@chnu.edu.ua`;
    await registerAndVerify(page, email, FRESH_PASSWORD);
    await signIn(page, dean, DEMO_PASSWORD);
    await openUser(page, email);

    await expect(page.getByTestId('detail-profile')).toBeVisible();
    await expect(page.getByTestId('legal-hold-card')).toHaveCount(0);
  });
});
