import AxeBuilder from '@axe-core/playwright';

import { expect, test } from './fixtures';

import { SEED, settled, signInAsSeed, sql } from './helpers';

type Page = import('@playwright/test').Page;

const PORTRAIT = { width: 360, height: 740 };
const LANDSCAPE = { width: 740, height: 360 };
const LANGUAGES = ['uk', 'en'] as const;
/** The smallest height of form actions, toolbar buttons and paginator buttons on a phone. */
const TOUCH_TARGET = 44;
const TARGETS = [
  '.award-form__actions .mat-mdc-button-base',
  'mat-toolbar .mat-mdc-button-base',
  'mat-paginator .mat-mdc-button-base',
];

function awardOf(email: string, status: string): string {
  return sql(
    `select a.award_id from awards a join users u on u.user_id = a.user_id
     where u.email_address = '${email}' and a.status = '${status}' order by a.award_id desc limit 1`,
  );
}

function shownAchievement(): string {
  return sql(
    `select award_id from awards where status = 'APPROVED' and visibility <> 'PRIVATE'
     order by award_id desc limit 1`,
  );
}

/** Leaves out the routes whose record does not exist on this database. */
function withIds(routes: string[]): string[] {
  return routes.filter((route) => route === '/' || !(route.includes('//') || route.endsWith('/')));
}

/**
 * Opens every route at both orientations in both languages and answers what does not fit a phone: sideways
 * scrolling, targets the axe rule `target-size` reports, and form, toolbar and paginator buttons under 44 px.
 */
async function sweep(
  page: Page,
  routes: string[],
): Promise<{ problems: string[]; measured: number }> {
  const problems: string[] = [];
  let measured = 0;
  for (const language of LANGUAGES) {
    await page.evaluate((lang) => localStorage.setItem('lang', lang), language);
    for (const route of withIds(routes)) {
      for (const size of [PORTRAIT, LANDSCAPE]) {
        await page.setViewportSize(size);
        await page.goto(route);
        await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => undefined);
        await settled(page);
        const where = `${route} ${size.width}x${size.height} ${language}`;
        if (
          page.url().includes('/login') ||
          (route !== '/forbidden' && page.url().endsWith('/forbidden'))
        ) {
          problems.push(`${where}: opened ${page.url()} instead`);
        }
        const overflow = await page.evaluate(
          () => document.documentElement.scrollWidth - window.innerWidth,
        );
        if (overflow > 0) {
          problems.push(`${where}: scrolls sideways by ${overflow}px`);
        }
        const axe = await new AxeBuilder({ page }).withRules(['target-size']).analyze();
        for (const violation of axe.violations) {
          problems.push(
            `${where}: target-size ${violation.nodes.map((node) => node.target).join(' | ')}`,
          );
        }
        if (size === PORTRAIT) {
          const heights = await page.evaluate(
            ({ selectors }) =>
              selectors.flatMap((selector) =>
                Array.from(document.querySelectorAll<HTMLElement>(selector))
                  .filter((element) => element.offsetParent !== null)
                  .map((element) => ({
                    label: `${selector} "${element.textContent?.trim().slice(0, 20)}"`,
                    height: Math.round(element.getBoundingClientRect().height),
                  })),
              ),
            { selectors: TARGETS },
          );
          measured += heights.length;
          problems.push(
            ...heights
              .filter((target) => target.height < TOUCH_TARGET)
              .map((target) => `${where}: ${target.label} ${target.height}px`),
          );
        }
      }
    }
  }
  return { problems, measured };
}

/** Sweeps the routes signed in; a sweep that met no toolbar or form button did not see the signed-in pages. */
async function signedInSweep(page: Page, email: string, routes: string[]): Promise<string[]> {
  await page.setViewportSize({ width: 1280, height: 800 });
  await signInAsSeed(page, email);
  const { problems, measured } = await sweep(page, routes);
  return measured > 0 ? problems : [...problems, `${email}: no touch target measured`];
}

test.describe('phone layouts', () => {
  test.describe.configure({ timeout: 600_000 });

  test('ac3_1 ac3_2 pages without a session fit a phone', async ({ page }) => {
    await page.goto('/public/achievements');
    const { problems } = await sweep(page, [
      '/public/achievements',
      '/public/units/9/achievements',
      '/privacy-notice',
      '/register',
      '/registration-pending',
      '/forgot-password',
      '/reset-password',
      '/verify-email',
      '/security/not-me',
      '/not-found',
      '/offline',
    ]);
    expect(problems).toEqual([]);
  });

  test('ac3_1 ac3_2 the pages of an employee fit a phone', async ({ page }) => {
    const draft = awardOf(SEED.employee, 'DRAFT');
    const approved = awardOf(SEED.employee, 'APPROVED');
    const pending = awardOf(SEED.employee, 'PENDING');
    const problems = await signedInSweep(page, SEED.employee, [
      '/',
      '/awards',
      '/awards/new',
      `/awards/${draft}/edit`,
      `/awards/${approved}`,
      `/awards/${pending}/submitted`,
      '/dashboard',
      '/dashboard/summary',
      '/achievements',
      `/achievements/${shownAchievement()}`,
      '/units/64/achievements',
      '/profile',
      '/privacy',
      '/notifications',
      '/forbidden',
    ]);
    expect(problems).toEqual([]);
  });

  test('ac3_1 ac3_2 the pages of reviewers and officers fit a phone', async ({ page }) => {
    const pending = awardOf(SEED.employee, 'PENDING');
    const employee = sql(`select user_id from users where email_address = '${SEED.employee}'`);
    const problems = [
      ...(await signedInSweep(page, SEED.dean, [
        '/reviews',
        `/awards/${pending}/correct`,
        '/delegations',
        '/analytics',
        '/analytics/units/9',
        '/admin/users',
        `/admin/users/${employee}`,
      ])),
    ];
    await page.context().clearCookies();
    await page.evaluate(() => sessionStorage.clear());
    problems.push(...(await signedInSweep(page, SEED.rector, ['/analytics/university'])));
    await page.context().clearCookies();
    await page.evaluate(() => sessionStorage.clear());
    problems.push(...(await signedInSweep(page, SEED.gdpr, ['/compliance'])));
    expect(problems).toEqual([]);
  });

  test('ac3_4 turning the phone keeps the values, the focus and the notice', async ({
    page,
    context,
  }) => {
    await signInAsSeed(page, SEED.employee);
    await page.setViewportSize(PORTRAIT);
    await page.goto('/awards/new');
    await page.getByTestId('award-title-uk').fill('Грамота');
    await page.getByTestId('award-organization').fill('МОН України');
    await context.setOffline(true);
    await expect(page.getByTestId('award-offline-notice')).toBeVisible();
    await page.getByTestId('award-organization').focus();

    await page.setViewportSize(LANDSCAPE);
    await page.setViewportSize(PORTRAIT);

    await expect(page.getByTestId('award-title-uk')).toHaveValue('Грамота');
    await expect(page.getByTestId('award-organization')).toBeFocused();
    await expect(page.getByTestId('award-offline-notice')).toBeVisible();
    await context.setOffline(false);
    await page.getByTestId('award-cancel').click();
    await page.getByTestId('confirm-accept').click();

    const manifest = await (await page.request.get('/brand/manifest.webmanifest')).json();
    expect(manifest.orientation).toBeUndefined();
  });
});
