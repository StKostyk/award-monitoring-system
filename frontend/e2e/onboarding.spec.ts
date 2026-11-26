import { Page, expect, test } from './fixtures';

import { FRESH_PASSWORD, freshEmployee, seriousViolations, signedInAs, sql } from './helpers';

/** The stored getting-started progress of an account, as the database holds it. */
function storedProgress(email: string): string {
  return sql(
    `select p.onboarding::text from dashboard_preferences p join users u on u.user_id = p.user_id
     where u.email_address = '${email}'`,
  );
}

async function dashboardOf(page: Page): Promise<void> {
  await page.goto('/dashboard');
  await expect(page.getByTestId('onboarding-checklist')).toBeVisible();
}

test.describe('getting started', () => {
  test('ac9_4_checklist_lists_the_steps_marks_an_opened_one_and_hides_for_good', async ({
    browser,
  }) => {
    const email = await freshEmployee(browser, 'onboarding');
    const page = await signedInAs(browser, email, FRESH_PASSWORD);
    await dashboardOf(page);

    const checklist = page.getByTestId('onboarding-checklist');
    await expect(checklist.getByRole('heading', { name: 'Перші кроки' })).toBeVisible();
    await expect(checklist).toContainText('Вітаємо, Ірина!');
    await expect(page.getByTestId('onboarding-ring')).toHaveText('1/5');
    await expect(page.getByTestId('onboarding-progress')).toHaveText('Виконано 1 з 5');
    await expect(page.getByTestId('onboarding-step-NOTICE')).toHaveAttribute('data-done', 'true');
    await expect(page.getByTestId('onboarding-step-PROFILE')).toHaveAttribute('data-next', 'true');
    expect(await seriousViolations(page)).toEqual([]);

    await page.getByTestId('onboarding-open-VISIBILITY').click();
    await expect(page).toHaveURL(/\/privacy#choices$/);
    await expect(page.getByTestId('privacy-choices-card')).toBeInViewport();

    await dashboardOf(page);
    await expect(page.getByTestId('onboarding-step-VISIBILITY')).toHaveAttribute(
      'data-done',
      'true',
    );
    await expect(page.getByTestId('onboarding-progress')).toHaveText('Виконано 2 з 5');

    await page.getByTestId('onboarding-dismiss').click();
    await expect(page.getByTestId('onboarding-checklist')).toHaveCount(0);
    await page.reload();
    await expect(page.getByTestId('dashboard-empty-first')).toBeVisible();
    await expect(page.getByTestId('onboarding-checklist')).toHaveCount(0);
    expect(storedProgress(email)).toContain('"dismissed": true');
  });

  test('ac9_5_tour_walks_four_steps_and_is_stored_as_seen', async ({ browser }) => {
    const email = await freshEmployee(browser, 'tour');
    const page = await signedInAs(browser, email, FRESH_PASSWORD);
    await dashboardOf(page);

    await page.getByTestId('onboarding-tour-start').click();
    const bubble = page.getByTestId('tour-bubble');
    await expect(page.getByTestId('tour-count')).toHaveText('Крок 1 з 4');
    await expect(bubble).toContainText('Ctrl K');
    await expect(page.getByTestId('tour-spotlight')).toBeVisible();
    await expect(page.getByTestId('tour-next')).toBeFocused();

    await page.getByTestId('tour-next').click();
    await expect(bubble).toHaveAttribute('data-stop', 'navigation');
    await page.getByTestId('tour-next').click();
    await expect(bubble).toHaveAttribute('data-stop', 'newAward');
    await page.getByTestId('tour-back').click();
    await expect(page.getByTestId('tour-count')).toHaveText('Крок 2 з 4');
    await page.getByTestId('tour-next').click();
    await page.getByTestId('tour-next').click();
    await expect(page.getByTestId('tour-count')).toHaveText('Крок 4 з 4');
    await expect(bubble).toContainText('Графік / Таблиця');

    await page.keyboard.press('Escape');
    await expect(page.getByTestId('onboarding-tour')).toHaveCount(0);
    await expect(page.getByTestId('onboarding-tour-start')).toBeFocused();
    await expect.poll(() => storedProgress(email)).toContain('"tourSeen": true');
    await expect(page.getByTestId('onboarding-checklist')).toBeVisible();
  });
});
