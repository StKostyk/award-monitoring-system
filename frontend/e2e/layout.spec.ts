import { expect, test } from './fixtures';
import { SEED, settled, signInAsSeed } from './helpers';
import { deanRoutes, openRoute } from './routes';

const DESKTOP = { width: 1280, height: 900 };
const PHONE = { width: 360, height: 780 };
const DESKTOP_BESIDE_SCROLLBAR = { width: 1264, height: 900 };
/** The height of a one-line side menu item. */
const ONE_LINE_ITEM = 48;

test.describe('page layout and shell (9.5.1)', () => {
  test('ac1_5 every Ukrainian sidebar label is shown in full and the active item covers both lines', async ({
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
    expect(labels.map((label) => label.label)).toContain('Аналітика університету');
    expect(labels.filter((label) => label.clipped || !label.inside)).toEqual([]);
    const active = labels.find((label) => label.active);
    expect(active?.label).toBe('Аналітика університету');
    expect(active?.lines).toBe(2);
    expect(active?.height).toBeGreaterThan(ONE_LINE_ITEM);
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
