import AxeBuilder from '@axe-core/playwright';
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';

import { expect, test } from './fixtures';

import {
  FRESH_PASSWORD,
  SEED,
  freshEmployee,
  settled,
  signIn,
  signInAsSeed,
  uniqueToken,
} from './helpers';
import {
  GDPR_ROUTES,
  PUBLIC_ROUTES,
  RECTOR_ROUTES,
  RoleRoutes,
  deanRoutes,
  employeeRoutes,
  openRoute,
} from './routes';

type Page = import('@playwright/test').Page;
type AxeResults = Awaited<ReturnType<AxeBuilder['analyze']>>;

const TAGS = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'];
const LANGUAGES = ['uk', 'en'] as const;
const SCHEMES = ['light', 'dark'] as const;
const DESKTOP = { width: 1280, height: 800 };
const PHONE = { width: 360, height: 740 };
const OPEN_STATES_MS = 180_000;
const UPLOAD_MS = 60_000;
const REPORT = join(process.cwd(), '..', 'build', 'accessibility-report.json');

/**
 * Findings that cannot be fixed in this application. Each entry excludes its selector from its own rule only and
 * names the reason and the issue that tracks it; a rule is never switched off as a whole.
 */
const EXCEPTIONS: { rule: string; selector: string; reason: string; issue: string }[] = [];

/** Moderate and minor findings per test, rule and page: the most nodes any one scan of the page reported. */
const minor: Record<string, Record<string, Record<string, number>>> = {};

interface Scan {
  page: Page;
  label: string;
  where: string;
  language: string;
  signedIn: boolean;
}

async function violations(page: Page): Promise<AxeResults['violations']> {
  const excepted = [...new Set(EXCEPTIONS.map((exception) => exception.rule))];
  const found = (await new AxeBuilder({ page }).withTags(TAGS).disableRules(excepted).analyze())
    .violations;
  for (const rule of excepted) {
    let builder = new AxeBuilder({ page }).withRules([rule]);
    for (const exception of EXCEPTIONS.filter((item) => item.rule === rule)) {
      builder = builder.exclude(exception.selector);
    }
    found.push(...(await builder.analyze()).violations);
  }
  return found;
}

/** Runs axe on the page and checks the page contract of 8.2.1; answers the serious and critical problems. */
async function scan({ page, label, where, language, signedIn }: Scan): Promise<string[]> {
  await settled(page);
  const report = (minor[test.info().title] ??= {});
  const problems: string[] = [];
  for (const violation of await violations(page)) {
    if (violation.impact === 'serious' || violation.impact === 'critical') {
      problems.push(
        `${where}: ${violation.id} ${violation.nodes.map((node) => node.target).join(' | ')}`,
      );
    } else {
      const rule = (report[violation.id] ??= {});
      rule[label] = Math.max(rule[label] ?? 0, violation.nodes.length);
    }
  }
  const contract = await page.evaluate(() => {
    const focusable = Array.from(
      document.querySelectorAll<HTMLElement>(
        'a[href], button, input, select, textarea, [tabindex]',
      ),
    ).find((element) => element.tabIndex >= 0 && !element.hasAttribute('disabled'));
    return {
      lang: document.documentElement.lang,
      title: document.title,
      first: focusable?.getAttribute('data-testid') ?? focusable?.tagName ?? null,
    };
  });
  if (contract.lang !== language) {
    problems.push(`${where}: lang is ${contract.lang}`);
  }
  if (!/^.+ — .+$/.test(contract.title)) {
    problems.push(`${where}: title "${contract.title}" names no page`);
  }
  if (signedIn && contract.first !== 'skip-link') {
    problems.push(`${where}: first focusable element is ${contract.first}`);
  }
  return problems;
}

/** Opens every route in both languages and schemes and scans it at desktop and phone width. */
async function sweep(page: Page, role: RoleRoutes): Promise<string[]> {
  const problems: string[] = [];
  for (const language of LANGUAGES) {
    await page.evaluate((lang) => localStorage.setItem('lang', lang), language);
    for (const scheme of SCHEMES) {
      await page.emulateMedia({ colorScheme: scheme });
      for (const route of role.routes) {
        await page.setViewportSize(DESKTOP);
        const elsewhere = await openRoute(page, route);
        if (elsewhere) {
          problems.push(`${route} ${language} ${scheme}: ${elsewhere}`);
          continue;
        }
        for (const size of [DESKTOP, PHONE]) {
          await page.setViewportSize(size);
          const where = `${route} ${language} ${scheme} ${size.width}`;
          const signedIn = role.account !== null;
          problems.push(...(await scan({ page, label: route, where, language, signedIn })));
        }
      }
    }
  }
  return problems;
}

async function signedInSweep(page: Page, role: RoleRoutes): Promise<string[]> {
  await page.setViewportSize(DESKTOP);
  await signInAsSeed(page, role.account ?? '');
  return sweep(page, role);
}

test.describe('accessibility of every route', () => {
  test.describe.configure({ timeout: 900_000 });

  test.afterAll(() => {
    const previous = existsSync(REPORT) ? JSON.parse(readFileSync(REPORT, 'utf-8')) : {};
    mkdirSync(dirname(REPORT), { recursive: true });
    writeFileSync(REPORT, `${JSON.stringify({ ...previous, ...minor }, null, 2)}\n`);
  });

  test('ac3_1 ac3_5 pages without a session', async ({ page }) => {
    await page.goto('/public/achievements');
    expect(await sweep(page, PUBLIC_ROUTES)).toEqual([]);
  });

  test('ac3_1 ac3_5 the pages of an employee', async ({ page }) => {
    expect(await signedInSweep(page, employeeRoutes())).toEqual([]);
  });

  test('ac3_1 ac3_5 the pages of a dean', async ({ page }) => {
    expect(await signedInSweep(page, deanRoutes())).toEqual([]);
  });

  test('ac3_1 ac3_5 the pages of the rector and the GDPR officer', async ({ browser }) => {
    const problems: string[] = [];
    for (const role of [RECTOR_ROUTES, GDPR_ROUTES]) {
      const context = await browser.newContext();
      try {
        problems.push(...(await signedInSweep(await context.newPage(), role)));
      } finally {
        await context.close();
      }
    }
    expect(problems).toEqual([]);
  });

  test('ac3_2 open states pass the same scan', async ({ browser, page, context }) => {
    test.setTimeout(OPEN_STATES_MS);
    const email = await freshEmployee(browser, 'a11y');
    await signIn(page, email, FRESH_PASSWORD);
    await expect(page.getByTestId('nav-awards')).toBeVisible();
    const problems: string[] = [];
    const check = async (state: string): Promise<void> => {
      problems.push(
        ...(await scan({ page, label: state, where: state, language: 'uk', signedIn: true })),
      );
    };

    await page.goto('/awards/new');
    await page.getByTestId('award-submit').click();
    await expect(page.getByTestId('award-form-error')).toBeVisible();
    await check('award form with errors');

    await page
      .locator('mat-form-field')
      .filter({ has: page.getByTestId('award-date') })
      .locator('mat-datepicker-toggle button')
      .click();
    await expect(page.locator('mat-calendar')).toBeVisible();
    await check('date picker');
    await page.keyboard.press('Escape');

    await page.getByTestId('award-title-uk').fill(`Грамота ${uniqueToken()}`);
    await page.getByTestId('award-save').click();
    await expect(page.getByTestId('award-form-message')).toBeVisible();
    await page.getByTestId('documents-input').setInputFiles({
      name: 'фото.png',
      mimeType: 'image/png',
      buffer: Buffer.from(
        'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==',
        'base64',
      ),
    });
    const stored = page.getByTestId('documents-list').getByTestId('document-name');
    await expect(stored).toBeVisible({ timeout: UPLOAD_MS });
    await stored.click();
    await expect(page.getByTestId('document-preview-image')).toBeVisible();
    await check('documents preview dialog');
    await page.getByTestId('document-preview-close').click();
    await expect(page.getByRole('dialog')).toHaveCount(0);

    await page.getByTestId('award-remove').click();
    await expect(page.getByRole('dialog')).toBeVisible();
    await check('delete draft dialog');
    await page.getByTestId('confirm-cancel').click();

    await page.getByTestId('user-menu').click();
    await expect(page.getByRole('menu')).toBeVisible();
    await check('profile menu');
    await page.keyboard.press('Escape');

    await page.setViewportSize(PHONE);
    await page.getByTestId('nav-toggle').click();
    await expect(page.getByTestId('side-nav')).toBeVisible();
    await check('navigation drawer at 360 px');
    await page.keyboard.press('Escape');

    await context.setOffline(true);
    await expect(page.getByTestId('offline-notice')).toBeVisible();
    await check('offline notice');
    await context.setOffline(false);

    const deanContext = await browser.newContext();
    try {
      const dean = await deanContext.newPage();
      await signInAsSeed(dean, SEED.dean);
      await dean.goto('/reviews');
      await dean.getByTestId('review-period-change').click();
      await expect(dean.getByRole('dialog')).toBeVisible();
      const state = 'review period dialog';
      problems.push(
        ...(await scan({ page: dean, label: state, where: state, language: 'uk', signedIn: true })),
      );
    } finally {
      await deanContext.close();
    }

    expect(problems).toEqual([]);
  });
});
