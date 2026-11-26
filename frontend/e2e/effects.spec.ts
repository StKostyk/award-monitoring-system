import { Page, expect, test } from './fixtures';

import { SEED, signedIn } from './helpers';

/** Long enough for a count-up and a stagger to have moved if they ran. */
const SETTLE_MS = 400;

async function tileValue(page: Page): Promise<string> {
  return (
    (await page.getByTestId('tile-approved').locator('.tile__value').textContent())?.trim() ?? ''
  );
}

async function runningAnimations(page: Page): Promise<number> {
  return page
    .locator('.tiles')
    .first()
    .evaluate(
      (tiles) =>
        tiles
          .getAnimations({ subtree: true })
          .filter((animation) => animation.playState === 'running').length,
    );
}

test.describe('effects and motion', () => {
  test('ac2_2 shows final numbers at once and no stagger under reduced motion', async ({
    browser,
  }) => {
    const page = await signedIn(browser, SEED.lecturer);
    await page.emulateMedia({ reducedMotion: 'reduce' });
    await page.goto('/dashboard');
    await expect(page.getByTestId('tile-approved')).toBeVisible();

    const first = await tileValue(page);
    expect(await runningAnimations(page)).toBe(0);
    await page.waitForTimeout(SETTLE_MS);

    expect(first).toMatch(/^[1-9]\d*$/);
    expect(await tileValue(page)).toBe(first);
    await expect(page.locator('.tiles .tile').first()).toHaveCSS('opacity', '1');
  });

  test('ac2_3 counts the numbers up to the same final figures with motion on', async ({
    browser,
  }) => {
    const still = await signedIn(browser, SEED.lecturer);
    await still.emulateMedia({ reducedMotion: 'reduce' });
    await still.goto('/dashboard');
    await expect(still.getByTestId('tile-approved')).toBeVisible();
    const final = await tileValue(still);
    await still.context().close();

    const page = await signedIn(browser, SEED.lecturer);
    await page.emulateMedia({ reducedMotion: 'no-preference' });
    await page.addInitScript(() => {
      const seen: string[] = [];
      Object.assign(window, { countSeen: seen });
      new MutationObserver(() => {
        const value = document.querySelector(
          '[data-testid="tile-approved"] .tile__value',
        )?.textContent;
        if (value && seen.at(-1) !== value) {
          seen.push(value);
        }
      }).observe(document, { subtree: true, childList: true, characterData: true });
    });
    await page.goto('/dashboard');

    await expect(page.getByTestId('tile-approved').locator('.tile__value')).toHaveText(final);
    await expect(page.locator('.tiles .tile').last()).toHaveCSS('opacity', '1');
    const seen = await page.evaluate(
      () => (window as unknown as { countSeen: string[] }).countSeen,
    );
    expect(seen.length).toBeGreaterThan(2);
    expect(seen.at(-1)).toBe(final);
  });
});
