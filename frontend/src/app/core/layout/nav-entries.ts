import { IconName } from '../../shared/icons/icons';
import {
  TokenPermissions,
  canCreateAwards,
  canDelegate,
  canManagePeople,
  canOpenCommandCentre,
  canOpenHallOfFame,
  canReadCompliance,
  canReadDirectory,
  canReadOwnAnalytics,
  canReadUnitAnalytics,
  canReview,
  roleHome,
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
  /** Whether an anonymous visitor sees the entry too. */
  readonly anonymous: boolean;
  readonly allowed: (permissions: TokenPermissions) => boolean;
}

const ANYONE = (): boolean => true;

/** Home stays in the menu only for a person whose roles open no start page of their own. */
const HOMELESS = (permissions: TokenPermissions): boolean => roleHome(permissions) === '/';

export const NAV_ENTRIES: readonly NavEntry[] = [
  entry('home', '/', 'app.home', 'house', HOMELESS, { exact: true, anonymous: true }),
  entry('hall-of-fame', '/hall-of-fame', 'hallOfFame.title', 'crown', canOpenHallOfFame),
  entry(
    'command-centre',
    '/command-centre',
    'commandCentre.title',
    'layout-dashboard',
    canOpenCommandCentre,
  ),
  entry('awards', '/awards', 'awards.title', 'award', canCreateAwards),
  entry('dashboard', '/dashboard', 'analytics.title', 'chart-line', canReadOwnAnalytics),
  entry('unit-analytics', '/analytics', 'titles.analytics', 'chart-column', canReadUnitAnalytics),
  entry('achievements', '/achievements', 'achievements.title', 'trophy', ANYONE),
  entry('reviews', '/reviews', 'reviews.title', 'clipboard-check', canReview),
  entry('users', '/admin/users', 'admin.users.title', 'users', canReadDirectory),
  entry('import', '/admin/import', 'admin.import.title', 'upload', canManagePeople),
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
  options: { exact?: boolean; anonymous?: boolean } = {},
): NavEntry {
  return {
    id,
    path,
    label,
    icon,
    allowed,
    exact: options.exact ?? false,
    anonymous: options.anonymous ?? false,
  };
}
