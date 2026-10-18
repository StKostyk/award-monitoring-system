import { expect, test } from '@playwright/test';

import {
  FRESH_PASSWORD,
  SEED,
  freshEmployee,
  seriousViolations,
  signIn,
  signInAsSeed,
} from './helpers';

const { gdpr } = SEED;
const POLICY_COUNT = 9;

test.describe('Compliance page', () => {
  test('ac25 the officer opens the three tabs and the url keeps tab and filter', async ({
    page,
  }) => {
    await signInAsSeed(page, gdpr);
    await page.getByTestId('nav-compliance').click();

    await expect(page).toHaveURL(/\/compliance$/);
    await expect(page.getByTestId('policies-table').locator('tbody tr')).toHaveCount(POLICY_COUNT);
    await expect(page.getByTestId('retention-loading')).toHaveCount(0);
    expect(await seriousViolations(page)).toEqual([]);

    await page.getByRole('tab', { name: 'Запити на видалення' }).click();

    await expect(page).toHaveURL(/tab=erasures/);
    await expect(page.getByTestId('erasures-table')).toBeVisible();
    await page.getByTestId('erasure-filter-COMPLETED').click();

    await expect(page).toHaveURL(/status=COMPLETED/);
    await expect(page.getByTestId('erasures-loading')).toHaveCount(0);
    expect(await seriousViolations(page)).toEqual([]);

    await page.getByRole('tab', { name: 'Юридичні утримання' }).click();
    await page.getByTestId('hold-filter-all').click();

    await expect(page).toHaveURL(/tab=holds/);
    await expect(page).toHaveURL(/holds=all/);
    await expect(page.getByTestId('holds-loading')).toHaveCount(0);
    expect(await seriousViolations(page)).toEqual([]);

    await page.reload();

    await expect(page.getByRole('tab', { name: 'Юридичні утримання' })).toHaveAttribute(
      'aria-selected',
      'true',
    );
    await expect(page.getByTestId('hold-filter-all')).toHaveClass(/checked/);

    await page.goBack();

    await expect(page).not.toHaveURL(/holds=all/);
    await expect(page.getByTestId('hold-filter-active')).toHaveClass(/checked/);

    await page.goBack();

    await expect(page).toHaveURL(/tab=erasures/);
    await expect(page.getByTestId('erasure-filter-COMPLETED')).toHaveClass(/checked/);
  });

  test('ac25 an employee has no entry and lands on the forbidden page', async ({
    browser,
    page,
  }) => {
    const employee = await freshEmployee(browser, 'compliance');
    await signIn(page, employee, FRESH_PASSWORD);

    await expect(page.getByTestId('nav-awards')).toBeVisible();
    await expect(page.getByTestId('nav-compliance')).toHaveCount(0);

    await page.goto('/compliance');

    await expect(page).toHaveURL(/\/forbidden$/);
  });
});
