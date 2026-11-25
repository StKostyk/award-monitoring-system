import { readFileSync } from 'node:fs';

import { expect, test } from './fixtures';

import {
  countMessages,
  FRESH_PASSWORD,
  registerAndVerify,
  seriousViolations,
  shellTool,
  signIn,
  sql,
  toggleLanguage,
} from './helpers';

test.describe('privacy centre', () => {
  test('ac2_3 ac2_4 ac2_6 ac8_1 ac8_2 opens from the account menu, stores the analytics choice and lists my activity', async ({
    page,
  }) => {
    const email = `e2e.privacy.${Date.now()}@chnu.edu.ua`;
    await registerAndVerify(page, email, FRESH_PASSWORD);
    await signIn(page, email, FRESH_PASSWORD);
    await expect(page.getByTestId('user-name')).toBeVisible();

    await (await shellTool(page, 'user-menu')).click();
    await page.getByTestId('nav-privacy').click();
    await expect(page).toHaveURL('http://localhost:4200/privacy');
    await expect(page.getByTestId('privacy-notice-acknowledged')).toContainText('1.1');
    await expect(page.getByTestId('privacy-awards-PRIVATE')).toHaveText('0');
    await expect(page.getByTestId('privacy-activity-action').first()).toHaveText('Вхід');
    await expect(page.getByTestId('privacy-status')).toContainText('Усе гаразд');
    await expect(page.getByTestId('privacy-email-off')).toContainText('про всі події');
    await expect(page.getByTestId('privacy-awards-link')).toHaveText('Змінити');
    await expect(page.getByTestId('privacy-download')).toHaveText('Завантажити');
    await expect(page.getByTestId('privacy-activity-mine')).toHaveAttribute('aria-pressed', 'true');
    await page.getByTestId('privacy-activity-about').click();
    await expect(page.getByTestId('privacy-activity-about')).toHaveAttribute(
      'aria-pressed',
      'true',
    );
    await expect(page.getByTestId('privacy-activity-action').getByText('Вхід')).toHaveCount(0);
    await page.getByTestId('privacy-activity-mine').click();

    const analytics = page.getByTestId('privacy-analytics').getByRole('switch');
    await expect(analytics).toHaveAttribute('aria-checked', 'false');
    await analytics.click();
    await expect(analytics).toHaveAttribute('aria-checked', 'true');
    await page.getByTestId('privacy-consent-history').click();
    await expect(page.getByTestId('privacy-consent-line').first()).toContainText('надано');
    expect(
      sql(
        `select count(*) from consent_records c join users u on u.user_id = c.user_id
         where u.email_address = '${email}' and c.consent_type = 'ANALYTICS' and c.is_granted`,
      ).trim(),
    ).toBe('1');

    await page.reload();
    await expect(analytics).toHaveAttribute('aria-checked', 'true');
    await expect(page.getByTestId('privacy-activity-action').first()).toHaveText('Згоду надано');
  });

  test('ac2_5 ac31 ac35 downloads my data from the privacy centre once a minute', async ({
    page,
  }) => {
    const email = `e2e.export.${Date.now()}@chnu.edu.ua`;
    await registerAndVerify(page, email, FRESH_PASSWORD);
    await signIn(page, email, FRESH_PASSWORD);
    await expect(page.getByTestId('user-name')).toBeVisible();
    await page.goto('/profile?section=privacy');
    await page.getByTestId('profile-privacy-export').click();
    await expect(page).toHaveURL('http://localhost:4200/privacy');

    const downloading = page.waitForEvent('download');
    await page.getByTestId('privacy-download').click();
    const download = await downloading;
    expect(download.suggestedFilename()).toMatch(
      /^award-monitoring-export-\d{4}-\d{2}-\d{2}\.json$/,
    );
    const body = readFileSync(await download.path(), 'utf-8');
    const file = JSON.parse(body) as {
      personal_data: { profile: { email: string } };
      roles: unknown[];
    };
    expect(file.personal_data.profile.email).toBe(email);
    expect(file.roles).toEqual([]);
    expect(body).not.toMatch(/password|\$2a\$|token/);
    await expect(page.getByTestId('privacy-export-done')).toBeVisible();
    await expect(page.getByTestId('privacy-last-export')).toBeVisible();

    await page.getByTestId('privacy-download').click();
    await expect(page.getByTestId('privacy-export-error')).toHaveText(
      'Забагато запитів. Спробуйте пізніше.',
    );
    await expect.poll(() => countMessages(email, 'Your data was exported')).toBe(1);
  });

  test('ac2_7 ac8_4 fits a phone and a desktop in both languages without serious axe findings', async ({
    page,
  }) => {
    const email = `e2e.privacy.layout.${Date.now()}@chnu.edu.ua`;
    await registerAndVerify(page, email, FRESH_PASSWORD);
    await signIn(page, email, FRESH_PASSWORD);
    await expect(page.getByTestId('user-name')).toBeVisible();

    for (const width of [360, 1280]) {
      await page.setViewportSize({ width, height: 800 });
      await page.goto('/privacy');
      await expect(page.getByTestId('privacy-activity-row').first()).toBeVisible();
      expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(
        width,
      );
      expect(await seriousViolations(page)).toEqual([]);
      if (width === 360) {
        const status = await page.getByTestId('privacy-status').boundingBox();
        const lefts = new Set<number>();
        let previousTop = status?.y ?? 0;
        for (const tile of await page.locator('app-bento-tile').all()) {
          const box = await tile.boundingBox();
          lefts.add(Math.round(box?.x ?? -1));
          expect(box?.y ?? 0).toBeGreaterThan(previousTop);
          previousTop = box?.y ?? 0;
        }
        expect(lefts.size).toBe(1);
      }
    }

    await toggleLanguage(page);
    await expect(
      page.getByRole('heading', { name: 'Privacy and my data', level: 1 }),
    ).toBeVisible();
    await page.emulateMedia({ colorScheme: 'dark' });
    expect(await seriousViolations(page)).toEqual([]);
  });
});
