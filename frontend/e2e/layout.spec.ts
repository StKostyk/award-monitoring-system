import { expect, test } from './fixtures';
import { SEED, settled, signInAsSeed, sql } from './helpers';
import { deanRoutes, openRoute } from './routes';

/** The demo employee whose approved awards the seed provides; only read here. */
const SEED_EMPLOYEE_WITH_AWARDS = 'employee.fmi@chnu.edu.ua';

const DESKTOP = { width: 1280, height: 900 };
const PHONE = { width: 360, height: 780 };
const DESKTOP_BESIDE_SCROLLBAR = { width: 1264, height: 900 };
/** The height of a one-line side menu item. */

test.describe('page layout and shell (9.5.1)', () => {
  test('ac1_5 every Ukrainian sidebar label is shown in full and the active item covers its label', async ({
    page,
  }) => {
    await page.setViewportSize(DESKTOP);
    await signInAsSeed(page, SEED.rector);
    await page.goto('/analytics/university');
    await settled(page);
    const items = page.locator('[data-testid="side-nav"] a[data-testid^="nav-"]');
    await expect(page.locator('[data-testid="side-nav"] a[aria-current="page"]')).toBeVisible();

    const labels = await items.evaluateAll((items) =>
      items.map((item) => {
        const title = item.querySelector<HTMLElement>('.mdc-list-item__primary-text')!;
        const text = title.getBoundingClientRect();
        const box = item.getBoundingClientRect();
        return {
          label: title.textContent?.trim(),
          clipped:
            title.scrollWidth > title.clientWidth + 1 ||
            title.scrollHeight > title.clientHeight + 1,
          inside: text.top >= box.top && text.bottom <= box.bottom,
          lines: Math.round(text.height / parseFloat(getComputedStyle(title).lineHeight)),
          active: item.getAttribute('aria-current') === 'page',
          height: box.height,
        };
      }),
    );
    expect(labels.filter((label) => label.clipped || !label.inside)).toEqual([]);
    const active = labels.find((label) => label.active);
    expect(active).toBeDefined();
    expect(active!.label).toBe('Аналітика');
  });

  test('ac1_1 ac1_4 the pages never scroll sideways at 360 and 1280', async ({ page }) => {
    const dean = deanRoutes();
    await page.setViewportSize(DESKTOP);
    await signInAsSeed(page, SEED.dean);
    const wide: string[] = [];
    for (const size of [DESKTOP, PHONE]) {
      await page.setViewportSize(size);
      for (const route of dean.routes) {
        const elsewhere = await openRoute(page, route);
        expect(elsewhere, route).toBeNull();
        const width = await page.evaluate(() => document.documentElement.scrollWidth);
        if (width > size.width) {
          wide.push(`${route} ${size.width}: ${width}`);
        }
      }
    }
    expect(wide).toEqual([]);
  });

  test('ac2_1 ac2_6 the personal dashboard splits at 1280 and stacks at 360 with the side column first', async ({
    page,
  }) => {
    await page.setViewportSize(DESKTOP);
    await signInAsSeed(page, SEED.lecturer);
    await page.goto('/dashboard');
    await expect(page.getByTestId('widget-RANK')).toBeVisible();
    const side = page.getByTestId('layout-side');
    const main = page.getByTestId('layout-main');
    await expect(side.getByTestId('profile-card')).toBeVisible();
    await expect(side.getByTestId('widget-KPI')).toBeVisible();
    const sideBox = (await side.boundingBox())!;
    const mainBox = (await main.boundingBox())!;
    expect(mainBox.x).toBeGreaterThan(sideBox.x + sideBox.width);
    expect(Math.abs(mainBox.y - sideBox.y)).toBeLessThan(2);

    await page.setViewportSize(PHONE);
    await expect(page.getByTestId('profile-card')).toBeVisible();
    const order = await page
      .locator('[data-testid="profile-card"], [data-testid^="widget-"]')
      .evaluateAll((nodes) =>
        nodes
          .map((node) => ({
            id: node.getAttribute('data-testid'),
            top: node.getBoundingClientRect().top,
          }))
          .sort((a, b) => a.top - b.top)
          .map((node) => node.id),
      );
    expect(order.slice(0, 7)).toEqual([
      'profile-card',
      'widget-KPI',
      'widget-FACT',
      'widget-TREND',
      'widget-LEVEL_STAIRS',
      'widget-IN_PROGRESS',
      'widget-LATEST',
    ]);
    const heading = (await page.getByTestId('page-heading').boundingBox())!;
    const action = (await page.getByTestId('dashboard-summary').boundingBox())!;
    expect(action.y).toBeGreaterThan(heading.y + heading.height - 1);
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(
      PHONE.width,
    );
  });

  test('ac1_1 ac8_1 the dashboard header keeps the context line, the title, the period and the actions on one row beside a scrollbar', async ({
    page,
  }) => {
    await page.setViewportSize(DESKTOP_BESIDE_SCROLLBAR);
    await signInAsSeed(page, SEED.rector);
    await page.goto('/dashboard');
    await expect(page.getByTestId('page-context')).toContainText('Затверджено');
    const heading = (await page.getByTestId('page-heading').boundingBox())!;
    for (const testId of [
      'period-from',
      'period-to',
      'dashboard-summary',
      'cv-export',
      'dashboard-new-award',
    ]) {
      const control = (await page.getByTestId(testId).boundingBox())!;
      expect(control.x).toBeGreaterThan(heading.x + heading.width);
      expect(control.y).toBeLessThan(heading.y + heading.height);
    }
    await expect(page.getByTestId('dashboard-summary')).toHaveAccessibleName('Підсумок');
    await expect(page.getByTestId('cv-export')).toHaveAccessibleName('Експорт для резюме');
  });
});

test.describe('widget and shell redesign (9.6)', () => {
  test('9.6 ac7_1 an award shows its facts beside the journey at 1280 and above it at 360', async ({
    page,
  }) => {
    const id = sql(
      `select a.award_id from awards a join users u on u.user_id = a.user_id
       where u.email_address = '${SEED_EMPLOYEE_WITH_AWARDS}' and a.status = 'APPROVED'
       order by a.award_id limit 1`,
    );
    expect(id).not.toBe('');
    await page.setViewportSize(DESKTOP);
    await signInAsSeed(page, SEED_EMPLOYEE_WITH_AWARDS);
    await page.goto(`/awards/${id}`);
    await expect(page.getByTestId('award-status-panel')).toBeVisible();
    const main = page.locator('.award-detail__main');
    const journey = page.getByTestId('award-status-panel');

    const wideMain = (await main.boundingBox())!;
    const wideJourney = (await journey.boundingBox())!;
    expect(wideJourney.x).toBeGreaterThanOrEqual(wideMain.x + wideMain.width);
    expect(wideJourney.y).toBeLessThan(wideMain.y + wideMain.height);

    await page.setViewportSize(PHONE);
    await expect(async () => {
      const narrowMain = (await main.boundingBox())!;
      const narrowJourney = (await journey.boundingBox())!;
      expect(narrowJourney.y).toBeGreaterThanOrEqual(narrowMain.y + narrowMain.height);
    }).toPass();
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(
      PHONE.width,
    );
  });

  for (const scheme of ['light', 'dark'] as const) {
    test(`9.6 ac1_7 a filled outlined field keeps its label clear of the border, ${scheme}`, async ({
      page,
    }) => {
      await page.emulateMedia({ colorScheme: scheme });
      await page.setViewportSize(DESKTOP);
      await signInAsSeed(page, SEED.employee);
      await page.goto('/profile?section=names');
      const field = page
        .locator('mat-form-field')
        .filter({ has: page.getByTestId('profile-first-name') });
      await expect(page.getByTestId('profile-first-name')).not.toHaveValue('');

      const geometry = await field.evaluate((host) => {
        const box = (selector: string) => host.querySelector(selector)!.getBoundingClientRect();
        const label = box('.mdc-floating-label');
        const notch = box('.mdc-notched-outline__notch');
        const input = box('input');
        return { label, notch, input };
      });
      expect(geometry.label.width).toBeGreaterThan(0);
      expect(geometry.label.left).toBeGreaterThanOrEqual(geometry.notch.left - 0.5);
      expect(geometry.label.right).toBeLessThanOrEqual(geometry.notch.right + 0.5);
      expect(geometry.label.bottom).toBeLessThanOrEqual(geometry.input.top + 0.5);
    });
  }
});

test.describe('back from a narrow pane (9.5.10)', () => {
  for (const back of ['browser', 'pane'] as const) {
    test(`ac10_1 ${back} back at 360 returns to the users list at its scroll position`, async ({
      page,
    }) => {
      await page.setViewportSize(PHONE);
      await signInAsSeed(page, SEED.admin);
      await page.goto('/admin/users');
      const links = page.getByTestId('user-link');
      await expect(links.nth(8)).toBeAttached();
      await settled(page);
      const scroller = page.locator('mat-sidenav-content');
      await scroller.evaluate(
        (element) => (element.scrollTop = Math.min(600, element.scrollHeight - 900)),
      );
      const before = await scroller.evaluate((element) => element.scrollTop);
      expect(before).toBeGreaterThan(200);
      const shown = await links.evaluateAll((rows) =>
        rows.findIndex((link) => {
          const box = link.getBoundingClientRect();
          return box.top > 250 && box.bottom < window.innerHeight;
        }),
      );
      expect(shown).toBeGreaterThanOrEqual(0);
      const row = links.nth(shown);
      const href = await row.getAttribute('href');
      await row.click();
      await expect(page).toHaveURL(href!);
      await expect(page.getByTestId('back-to-users')).toBeVisible();

      if (back === 'browser') {
        await page.goBack();
      } else {
        await page.getByTestId('back-to-users').click();
      }

      const chosen = page.locator(`[data-testid="user-link"][href="${href}"]`);
      await expect(chosen).toBeFocused();
      await expect(chosen).toBeInViewport();
      await expect
        .poll(() =>
          scroller.evaluate((element, start) => Math.abs(element.scrollTop - start), before),
        )
        .toBeLessThan(40);
    });
  }
});
