import { expect, test } from './fixtures';

import { DEMO_PASSWORD, SEED, seriousViolations, signIn, uniqueToken } from './helpers';

const DEPARTMENT = 'Кафедра алгебри';
const LEVEL = 'Університетський';

function surname(): string {
  const token = uniqueToken();
  return `Імпорт${token.charAt(0).toUpperCase()}${token.substring(1)}`;
}

function spreadsheet(lastName: string, title: string): Buffer {
  const rows = ['ПІБ;Назва;Дата;Ким видано', `${lastName} Петро;${title};2001;ЧНУ`];
  return Buffer.from(rows.join('\r\n'), 'utf-8');
}

test.describe('award import', () => {
  test('ac5_1 ac5_2 ac5_3 the secretary maps, previews and imports a past award of a new colleague', async ({
    page,
  }) => {
    const lastName = surname();
    const title = `Подяка ректора ${lastName}`;
    await signIn(page, SEED.secretary, DEMO_PASSWORD);
    await page.getByTestId('nav-import').first().click();

    await expect(page).toHaveURL(/\/admin\/import$/);
    await page.getByTestId('import-input').setInputFiles({
      name: 'awards.csv',
      mimeType: 'text/csv',
      buffer: spreadsheet(lastName, title),
    });

    const row = page.getByTestId('import-row-2');
    await expect(row).toHaveClass(/import__row--blocked/, { timeout: 20_000 });
    await expect(row).toContainText('Немає кафедри');
    await expect(row).toContainText('Немає рівня');
    await expect(page.getByTestId('import-submit')).toBeDisabled();

    await page.getByTestId('import-department').click();
    await page.getByRole('option', { name: DEPARTMENT }).click();
    await page.getByTestId('import-level').click();
    await page.getByRole('option', { name: LEVEL, exact: true }).click();
    await expect(page.getByTestId('import-changed')).toBeVisible();
    await page.getByTestId('import-refresh').click();

    await expect(row).not.toHaveClass(/import__row--blocked/);
    await expect(row).toContainText(`${lastName} Петро`);
    await expect(row).toContainText('Буде додано');
    await expect(page.getByTestId('import-summary')).toContainText('До імпорту: 1');
    expect(await seriousViolations(page)).toEqual([]);
    await page.getByTestId('import-submit').click();

    await expect(page.getByTestId('import-result-imported')).toContainText(
      'Імпортовано нагород: 1',
    );
    await expect(page.getByTestId('import-result-people')).toContainText('1');

    await page.goto('/admin/users');
    await page.getByTestId('filter-q').fill(lastName);
    await page.getByRole('row').filter({ hasText: lastName }).getByTestId('user-link').click();
    await page.getByTestId('person-award-link').click();

    await expect(page.getByTestId('award-detail-title')).toHaveText(title);
    await expect(page.getByTestId('award-detail-status')).toHaveText('Затверджено');
    await expect(page.getByTestId('award-detail-imported')).toHaveText('Імпортовано');
  });

  test('ac5_2 importing the same file again adds nothing', async ({ page }) => {
    const lastName = surname();
    const file = {
      name: 'awards.csv',
      mimeType: 'text/csv',
      buffer: spreadsheet(lastName, `Грамота ${lastName}`),
    };
    await signIn(page, SEED.secretary, DEMO_PASSWORD);
    await page.goto('/admin/import');

    for (const expected of [1, 0]) {
      await page.getByTestId('import-input').setInputFiles(file);
      await page.getByTestId('import-department').click();
      await page.getByRole('option', { name: DEPARTMENT }).click();
      await page.getByTestId('import-level').click();
      await page.getByRole('option', { name: LEVEL, exact: true }).click();
      await page.getByTestId('import-refresh').click();
      if (expected === 0) {
        await expect(page.getByTestId('import-row-2')).toContainText('Уже збережено');
        await expect(page.getByTestId('import-submit')).toBeDisabled();
        break;
      }
      await page.getByTestId('import-submit').click();
      await expect(page.getByTestId('import-result-imported')).toContainText(`${expected}`);
      await page.getByTestId('import-again').click();
    }
  });

  test('ac5_4 an employee is refused the import page', async ({ page }) => {
    await signIn(page, SEED.employee, DEMO_PASSWORD);
    await page.goto('/admin/import');

    await expect(page).toHaveURL(/\/forbidden$/);
  });
});
