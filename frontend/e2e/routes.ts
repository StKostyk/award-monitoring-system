import { SEED, sql } from './helpers';

type Page = import('@playwright/test').Page;

/** The routes of every role, taken from the route files; a record route is left out when its record is missing. */
export interface RoleRoutes {
  /** The seed account that sees the routes; null for pages without a session. */
  account: string | null;
  routes: string[];
}

export const PUBLIC_ROUTES: RoleRoutes = {
  account: null,
  routes: [
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
  ],
};

/** The pages of an employee, with the records of the seed employee. */
export function employeeRoutes(): RoleRoutes {
  return {
    account: SEED.employee,
    routes: withIds([
      '/',
      '/awards',
      '/awards/new',
      `/awards/${awardOf(SEED.employee, 'DRAFT')}/edit`,
      `/awards/${awardOf(SEED.employee, 'APPROVED')}`,
      `/awards/${awardOf(SEED.employee, 'PENDING')}/submitted`,
      '/dashboard',
      '/dashboard/summary',
      '/achievements',
      `/achievements/${shownAchievement()}`,
      '/units/64/achievements',
      '/profile',
      '/privacy',
      '/notifications',
      '/forbidden',
    ]),
  };
}

/** The pages of a dean: reviews, corrections, delegations, unit analytics and the user directory. */
export function deanRoutes(): RoleRoutes {
  const employee = sql(`select user_id from users where email_address = '${SEED.employee}'`);
  return {
    account: SEED.dean,
    routes: withIds([
      '/reviews',
      `/awards/${awardOf(SEED.employee, 'PENDING')}/correct`,
      '/delegations',
      '/analytics',
      '/analytics/units/9',
      '/admin/users',
      `/admin/users/${employee}`,
    ]),
  };
}

export const RECTOR_ROUTES: RoleRoutes = {
  account: SEED.rector,
  routes: ['/analytics/university'],
};

export const GDPR_ROUTES: RoleRoutes = { account: SEED.gdpr, routes: ['/compliance'] };

/**
 * Opens a route and waits for its data; answers what opened instead when the session or the role did not reach
 * it (the sign-in page, the forbidden page), or null.
 */
export async function openRoute(page: Page, route: string): Promise<string | null> {
  await page.goto(route);
  await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => undefined);
  const url = page.url();
  return url.includes('/login') || (route !== '/forbidden' && url.endsWith('/forbidden'))
    ? `opened ${url} instead`
    : null;
}

/** Ends the session of the page, so that another account can sign in. */
export async function signOutQuietly(page: Page): Promise<void> {
  await page.context().clearCookies();
  await page.evaluate(() => sessionStorage.clear());
}

/** The newest award of a user in a status, or an empty text. */
export function awardOf(email: string, status: string): string {
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

function withIds(routes: string[]): string[] {
  return routes.filter((route) => route === '/' || !(route.includes('//') || route.endsWith('/')));
}
