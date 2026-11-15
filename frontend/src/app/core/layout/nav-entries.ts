import { IconName } from '../../shared/icons/icons';
import {
  TokenPermissions,
  canDelegate,
  canReadCompliance,
  canReadDirectory,
  canReadOwnAnalytics,
  canReadOwnAwards,
  canReadUnitAnalytics,
  canReadUniversityAnalytics,
  canReview,
} from '../auth/permissions';

/** A section of the app offered in the side menu and in the command palette. */
export interface NavEntry {
  /** Suffix of the `nav-` test id. */
  readonly id: string;
  readonly path: string;
  /** Translation key of the section name. */
  readonly label: string;
  readonly icon: IconName;
  /** Whether only the path itself, not its children, marks the entry current. */
  readonly exact: boolean;
  /** A child path that belongs to another entry and does not mark this one current. */
  readonly excludes: string | null;
  /** Whether an anonymous visitor sees the entry too. */
  readonly anonymous: boolean;
  readonly allowed: (permissions: TokenPermissions) => boolean;
}

const ANYONE = (): boolean => true;

export const NAV_ENTRIES: readonly NavEntry[] = [
  entry('home', '/', 'app.home', 'house', ANYONE, { exact: true, anonymous: true }),
  entry('awards', '/awards', 'awards.title', 'award', canReadOwnAwards),
  entry('dashboard', '/dashboard', 'analytics.title', 'chart-line', canReadOwnAnalytics),
  entry(
    'unit-analytics',
    '/analytics',
    'unitAnalytics.title',
    'chart-column',
    canReadUnitAnalytics,
    {
      excludes: '/analytics/university',
    },
  ),
  entry(
    'university-analytics',
    '/analytics/university',
    'universityAnalytics.title',
    'landmark',
    canReadUniversityAnalytics,
  ),
  entry('achievements', '/achievements', 'achievements.title', 'trophy', ANYONE),
  entry('reviews', '/reviews', 'reviews.title', 'clipboard-check', canReview),
  entry('users', '/admin/users', 'admin.users.title', 'users', canReadDirectory),
  entry('delegations', '/delegations', 'delegations.title', 'arrow-left-right', canDelegate),
  entry('compliance', '/compliance', 'compliance.title', 'shield-check', canReadCompliance),
];

/**
 * The sections a person may open, by the same permission helpers as the route guards.
 *
 * @param authenticated whether the person is signed in; an anonymous visitor gets the home page only
 * @param permissions the permissions of the access token
 * @return the allowed entries in menu order
 */
export function navEntriesFor(authenticated: boolean, permissions: TokenPermissions): NavEntry[] {
  return authenticated
    ? NAV_ENTRIES.filter((item) => item.allowed(permissions))
    : NAV_ENTRIES.filter((item) => item.anonymous);
}

function entry(
  id: string,
  path: string,
  label: string,
  icon: IconName,
  allowed: (permissions: TokenPermissions) => boolean,
  options: { exact?: boolean; excludes?: string; anonymous?: boolean } = {},
): NavEntry {
  return {
    id,
    path,
    label,
    icon,
    allowed,
    exact: options.exact ?? false,
    excludes: options.excludes ?? null,
    anonymous: options.anonymous ?? false,
  };
}
