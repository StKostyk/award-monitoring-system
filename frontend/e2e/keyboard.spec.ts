import { expect, test } from './fixtures';

import {
  FRESH_PASSWORD,
  SEED,
  freshEmployee,
  pastDay,
  signIn,
  signInAsSeed,
  uniqueToken,
} from './helpers';

type Page = import('@playwright/test').Page;

/** Tab stops tried before a control counts as unreachable by keyboard. */
const MAX_TABS = 80;

/** Tab stops checked for a focus indicator on each screen: the shell and the first controls of the page. */
const INDICATOR_STOPS = 40;

/** Presses Tab until the element with the test id has the focus; fails when it is never reached. */
async function tabTo(page: Page, testId: string): Promise<void> {
  for (let presses = 0; presses < MAX_TABS; presses++) {
    await page.keyboard.press('Tab');
    const reached = await page.evaluate(
      (id) => !!document.activeElement?.closest(`[data-testid="${id}"]`),
      testId,
    );
    if (reached) {
      return;
    }
  }
  throw new Error(`${testId} is not reachable with Tab`);
}

function focusedTag(page: Page): Promise<string | undefined> {
  return page.evaluate(() => document.activeElement?.tagName);
}

test.describe('keyboard and page structure', () => {
  test('ac1_1 ac1_2 language and title follow the interface', async ({ page }) => {
    await signInAsSeed(page, SEED.employee);
    await page.goto('/awards');
    await expect(page).toHaveTitle(/^Мої нагороди — /);
    expect(await page.evaluate(() => document.documentElement.lang)).toBe('uk');

    await page.getByTestId('language-toggle').click();

    await expect(page).toHaveTitle(/^My awards — /);
    expect(await page.evaluate(() => document.documentElement.lang)).toBe('en');
    await page.getByTestId('language-toggle').click();
    await expect(page).toHaveTitle(/^Мої нагороди — /);
  });

  test('ac1_3 ac1_4 the skip link and the focus after navigation', async ({ page }) => {
    await signInAsSeed(page, SEED.employee);
    await page.goto('/awards');
    await expect(page.getByTestId('award-add')).toBeVisible();

    await page.keyboard.press('Tab');
    await expect(page.getByTestId('skip-link')).toBeFocused();
    await expect(page.getByTestId('skip-link')).toBeInViewport();
    await page.keyboard.press('Enter');
    expect(await focusedTag(page)).toBe('MAIN');
    await page.keyboard.press('Tab');
    expect(
      await page.evaluate(() => !!document.activeElement?.closest('[data-testid="side-nav"]')),
    ).toBe(false);

    await page.getByTestId('award-add').focus();
    await page.keyboard.press('Enter');
    await expect(page).toHaveURL(/\/awards\/new$/);
    await expect(page.locator('main h1')).toBeFocused();
    await expect(page).toHaveTitle(/^Нова нагорода — /);

    await page.goBack();
    await expect(page).toHaveURL(/\/awards$/);
    await expect(page.locator('main h1')).toBeFocused();
  });

  test('ac1_5 every focused control shows an indicator', async ({ page }) => {
    await signInAsSeed(page, SEED.employee);
    const screens = [
      { path: '/awards/new', ready: 'award-title-uk' },
      { path: '/awards', ready: 'award-add' },
    ];
    for (const [scheme, screen] of (['light', 'dark'] as const).flatMap((each) =>
      screens.map((item) => [each, item] as const),
    )) {
      await page.emulateMedia({ colorScheme: scheme });
      await page.goto(screen.path);
      await expect(page.getByTestId(screen.ready)).toBeVisible();
      const missing: string[] = [];
      for (let stop = 0; stop < INDICATOR_STOPS; stop++) {
        await page.keyboard.press('Tab');
        const result = await page.evaluate(() => {
          const element = document.activeElement as HTMLElement | null;
          if (!element || element === document.body) {
            return null;
          }
          const style = getComputedStyle(element);
          const outline = style.outlineStyle !== 'none' && parseFloat(style.outlineWidth) > 0;
          const indicator =
            (element.matches('.mat-focus-indicator') ? element : null) ??
            element.querySelector(':scope > .mat-focus-indicator') ??
            element.closest('.mat-focus-indicator') ??
            element;
          const ring = getComputedStyle(indicator, '::before');
          const materialRing =
            ring.content !== 'none' &&
            ring.borderStyle !== 'none' &&
            parseFloat(ring.borderWidth) > 0;
          const field = !!element.closest('.mat-focused');
          return outline || materialRing || field
            ? null
            : `${element.tagName}.${element.className}`.slice(0, 80);
        });
        if (result) {
          missing.push(result);
        }
      }
      expect(missing, `${screen.path}, ${scheme}`).toEqual([]);
    }
  });

  test('ac1_6 dialogs open without motion when the system asks for it', async ({ page }) => {
    await page.emulateMedia({ reducedMotion: 'reduce' });
    await signInAsSeed(page, SEED.employee);
    await page.goto('/awards/new');
    await page.getByTestId('award-title-uk').fill('Грамота');
    await page.getByTestId('award-cancel').click();

    await expect(page.getByRole('dialog')).toBeVisible();
    const running = await page.evaluate(
      () =>
        document
          .getAnimations()
          .filter((animation) => animation.playState === 'running')
          .filter((animation) => Number(animation.effect?.getComputedTiming().duration ?? 0) > 1)
          .length,
    );
    expect(running).toBe(0);
    await page.getByTestId('confirm-cancel').click();
  });

  test('ac1_7 an award is entered and submitted with the keyboard only', async ({ browser }) => {
    const email = await freshEmployee(browser, 'keyboard');
    const context = await browser.newContext();
    try {
      await enterAndSubmit(await context.newPage(), email);
    } finally {
      await context.close();
    }
  });

  test('ac1_4 a confirmed leave focuses the next page and a cancelled one stays', async ({
    page,
  }) => {
    await signInAsSeed(page, SEED.employee);
    await page.goto('/awards/new');
    await page.getByTestId('award-title-uk').fill('Грамота');

    await page.getByTestId('nav-awards').click();
    await page.getByTestId('confirm-cancel').click();
    await expect(page.getByRole('dialog')).toHaveCount(0);
    await expect(page).toHaveURL(/\/awards\/new$/);
    await expect(page).toHaveTitle(/^Нова нагорода — /);

    await page.getByTestId('nav-awards').click();
    await page.getByTestId('confirm-accept').click();
    await expect(page).toHaveURL(/\/awards$/);
    await expect(page.locator('main h1')).toBeFocused();
    await expect(page).toHaveTitle(/^Мої нагороди — /);
  });

  test('ac1_2 an unknown path shows the not-found page under its own address', async ({ page }) => {
    await page.goto('/not-a-page');
    await expect(page.getByTestId('not-found-card')).toBeVisible();
    await expect(page).toHaveURL(/\/not-a-page$/);
    await expect(page).toHaveTitle(/^Сторінку не знайдено — /);

    await signInAsSeed(page, SEED.employee);
    await page.goto('/awards/1/unknown');
    await expect(page.getByTestId('not-found-card')).toBeVisible();
    await expect(page).toHaveTitle(/^Сторінку не знайдено — /);
    await page.getByTestId('language-toggle').click();
    await expect(page).toHaveTitle(/^Page not found — /);
    await page.getByTestId('language-toggle').click();
  });
});

async function enterAndSubmit(page: Page, email: string): Promise<void> {
  await signIn(page, email, FRESH_PASSWORD);
  await expect(page.getByTestId('nav-awards')).toBeVisible();

  await tabTo(page, 'nav-awards');
  await page.keyboard.press('Enter');
  await expect(page).toHaveURL(/\/awards$/);
  await tabTo(page, 'award-add');
  await page.keyboard.press('Enter');
  await expect(page).toHaveURL(/\/awards\/new$/);

  await tabTo(page, 'award-title-uk');
  await page.keyboard.type(`Грамота з клавіатури ${uniqueToken()}`);
  await tabTo(page, 'award-category');
  await page.keyboard.press('Enter');
  for (let step = 0; step < 30; step++) {
    const active = await page.evaluate(() =>
      document.querySelector('mat-option.mat-mdc-option-active')?.getAttribute('data-testid'),
    );
    if (active === 'category-option-13') {
      break;
    }
    await page.keyboard.press('ArrowDown');
  }
  await expect(page.getByTestId('category-option-13')).toHaveClass(/mat-mdc-option-active/);
  await page.keyboard.press('Enter');
  await tabTo(page, 'award-organization');
  await page.keyboard.type('Міністерство освіти і науки України');
  await tabTo(page, 'award-date');
  await page.keyboard.type(pastDay());

  await tabTo(page, 'documents-drop');
  const chooser = page.waitForEvent('filechooser');
  await page.keyboard.press('Enter');
  await (
    await chooser
  ).setFiles({
    name: 'грамота.pdf',
    mimeType: 'application/pdf',
    buffer: Buffer.from(`%PDF-1.4\n% ${uniqueToken()}\n${' '.repeat(2048)}`),
  });
  await expect(page.getByTestId('documents-list').locator('li')).toHaveCount(1);

  await tabTo(page, 'award-submit');
  await page.keyboard.press('Enter');
  await expect(page).toHaveURL(/\/awards\/\d+\/submitted$/);

  await page.goBack();
  await expect(page).not.toHaveURL(/\/submitted$/);
  await expect(page.locator('main').locator('h1, h2, mat-card-title').first()).toBeFocused();
  await expect(page).toHaveTitle(/^.+ — .+$/);
}
