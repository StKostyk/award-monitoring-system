import { Page, expect, test } from './fixtures';

import { SEED, signedIn } from './helpers';

/** Faculty of Mathematics and Informatics (seed). */
const FMI = 9;

const KEY_PATTERN =
  /\b(?:analytics|unitAnalytics|universityAnalytics|categories|app)\.[A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)*/g;

async function visibleKeys(page: Page): Promise<string[]> {
  const texts = await page.evaluate(() => [
    document.body.innerText,
    ...Array.from(document.querySelectorAll('[aria-label],[title]')).flatMap((node) => [
      node.getAttribute('aria-label') ?? '',
      node.getAttribute('title') ?? '',
    ]),
  ]);
  return [...new Set(texts.flatMap((text) => text.match(KEY_PATTERN) ?? []))];
}

async function switchAndCheck(page: Page, title: string, englishTitle: string): Promise<void> {
  const heading = page.getByRole('heading', { level: 1 });
  await expect(heading).toHaveText(title);
  await expect(page.getByTestId('section-highlights')).toBeVisible();

  await page.getByTestId('language-toggle').click();
  await expect(heading).toHaveText(englishTitle);
  await expect.poll(() => visibleKeys(page)).toEqual([]);

  await page.getByTestId('language-toggle').click();
  await expect(heading).toHaveText(title);
  await expect.poll(() => visibleKeys(page)).toEqual([]);
}

test.describe('analytics language switch', () => {
  test('scrum112 ac1 ac2 shows no translation key on the university page after a switch', async ({
    browser,
  }) => {
    const page = await signedIn(browser, SEED.rector);
    await page.goto('/analytics/university');

    await switchAndCheck(page, 'Аналітика університету', 'University analytics');
  });

  test('scrum112 ac1 ac2 shows no translation key on the unit page after a switch', async ({
    browser,
  }) => {
    const page = await signedIn(browser, SEED.dean);
    await page.goto(`/analytics/units/${FMI}`);

    await switchAndCheck(page, 'Аналітика підрозділів', 'Unit analytics');
  });
});
