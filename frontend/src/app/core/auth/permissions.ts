import { RoleType } from './user-profile';

/** One role the caller holds in one organisation, read from the `role_scopes` claim. */
export interface RoleScope {
  role: RoleType;
  organizationId: number;
}

/** One role the caller holds only by delegation, read from the `delegations` claim. */
export interface DelegationScope extends RoleScope {
  delegatorId: number;
}

/** What the current access token carries; used to hide controls, never to authorise. */
export interface TokenPermissions {
  permissions: string[];
  roleScopes: RoleScope[];
  heldScopes: RoleScope[];
  delegations: DelegationScope[];
  hasPermission(permission: string): boolean;
  canGrant(role: RoleType): boolean;
}

/** Roles ordered by level. */
const LEVELS: RoleType[] = ['EMPLOYEE', 'FACULTY_SECRETARY', 'DEAN', 'RECTOR_SECRETARY', 'RECTOR'];

/** The roles each role may grant; `SYSTEM_ADMIN` grants every role, anybody else nothing. */
const GRANTS: Partial<Record<RoleType, RoleType[]>> = {
  FACULTY_SECRETARY: ['EMPLOYEE'],
  DEAN: ['FACULTY_SECRETARY', 'EMPLOYEE'],
  RECTOR_SECRETARY: ['EMPLOYEE'],
  RECTOR: ['RECTOR_SECRETARY', 'DEAN'],
};

/** Roles whose home is the Hall of Fame. */
const HALL_OF_FAME_ROLES: RoleType[] = ['RECTOR', 'DEAN'];

/** Roles whose Command centre shows a faculty, in the order the page prefers them. */
const FACULTY_DESK_ROLES: RoleType[] = ['FACULTY_SECRETARY', 'DEAN'];

/** The queue level and the faculty of the Command centre; a null faculty means the faculties of the university. */
export interface CommandCentreScope {
  level: 'FACULTY_SECRETARY' | 'DEAN' | 'RECTOR_SECRETARY';
  facultyId: number | null;
}

/** Permissions that open the user directory. */
export const DIRECTORY_PERMISSIONS = ['user:read:scope', 'user:read:all'];

/** Roles whose approval authority may be handed to a colleague. */
export const APPROVAL_ROLES: RoleType[] = [
  'FACULTY_SECRETARY',
  'DEAN',
  'RECTOR_SECRETARY',
  'RECTOR',
];

export const NO_PERMISSIONS: TokenPermissions = build([], [], [], []);

/**
 * Reads the `permissions`, `role_scopes`, `held_scopes` and `delegations` claims; anything unreadable yields
 * nothing.
 */
export function readPermissions(token: string | null | undefined): TokenPermissions {
  const claims = payload(token);
  if (!claims) {
    return NO_PERMISSIONS;
  }
  const roleScopes = scopes(strings(claims['role_scopes']));
  const delegated = delegationScopes(strings(claims['delegations']));
  const held =
    claims['held_scopes'] === undefined ? undefined : scopes(strings(claims['held_scopes']));
  return build(
    strings(claims['permissions']),
    roleScopes,
    held ?? withoutBorrowed(roleScopes, delegated),
    delegated,
  );
}

/** The user id (`sub` claim) of the token, or null when it cannot be read. */
export function readSubject(token: string | null | undefined): string | null {
  const subject = payload(token)?.['sub'];
  return typeof subject === 'string' && subject !== '' ? subject : null;
}

/** Whether the caller may enter awards. */
export function canCreateAwards(permissions: TokenPermissions): boolean {
  return permissions.hasPermission('award:create');
}

/** Whether the caller may change their own drafts. */
export function canEditOwnAwards(permissions: TokenPermissions): boolean {
  return permissions.hasPermission('award:update:own');
}

/** Whether the caller may open their own awards. */
export function canReadOwnAwards(permissions: TokenPermissions): boolean {
  return permissions.hasPermission('award:read:own');
}

/** Whether the caller may open their personal dashboard. */
export function canReadOwnAnalytics(permissions: TokenPermissions): boolean {
  return permissions.hasPermission('analytics:read:own');
}

/** Whether the caller may open the analytics of the faculties and departments in their scope. */
export function canReadUnitAnalytics(permissions: TokenPermissions): boolean {
  return permissions.hasPermission('analytics:read:department');
}

/** Whether the caller may open the analytics of the whole university and its compliance summary. */
export function canReadUniversityAnalytics(permissions: TokenPermissions): boolean {
  return permissions.hasPermission('analytics:read:university');
}

/**
 * Whether the caller holds the rector or dean role of their own and so opens the Hall of Fame; a borrowed role
 * does not count, since delegations never carry analytics.
 */
export function canOpenHallOfFame(permissions: TokenPermissions): boolean {
  return permissions.heldScopes.some((scope) => HALL_OF_FAME_ROLES.includes(scope.role));
}

/**
 * Whether the caller holds a faculty secretary, dean or rector's secretary role of their own and so opens the
 * Command centre; a borrowed role does not count, since delegations never carry analytics.
 */
export function canOpenCommandCentre(permissions: TokenPermissions): boolean {
  return commandCentreScope(permissions) !== null;
}

/**
 * What the Command centre of the caller shows: the faculty of a held faculty secretary role, else of a held dean
 * role, else the faculties of the university for a rector's secretary.
 *
 * @param permissions the permissions of the access token
 * @return the scope, or null when the caller holds none of these roles
 */
export function commandCentreScope(permissions: TokenPermissions): CommandCentreScope | null {
  for (const role of FACULTY_DESK_ROLES) {
    const scope = permissions.heldScopes.find((held) => held.role === role);
    if (scope) {
      return { level: role as CommandCentreScope['level'], facultyId: scope.organizationId };
    }
  }
  return permissions.heldScopes.some((held) => held.role === 'RECTOR_SECRETARY')
    ? { level: 'RECTOR_SECRETARY', facultyId: null }
    : null;
}

/** Whether the Hall of Fame of the caller shows the whole university rather than a faculty. */
export function readsUniversityHallOfFame(permissions: TokenPermissions): boolean {
  return (
    permissions.heldScopes.some((scope) => scope.role === 'RECTOR') &&
    canReadUniversityAnalytics(permissions)
  );
}

/** Whether the caller may open the compliance page: retention runs, erasure requests and legal holds. */
export function canReadCompliance(permissions: TokenPermissions): boolean {
  return permissions.hasPermission('compliance:read');
}

/** Whether the caller may open the user directory. */
export function canReadDirectory(permissions: TokenPermissions): boolean {
  return DIRECTORY_PERMISSIONS.some((permission) => permissions.hasPermission(permission));
}

/** Roles that add and manage people without an account, held or lent. */
const PEOPLE_ROLES: RoleType[] = ['FACULTY_SECRETARY', 'DEAN'];

/** Whether the caller may add and manage colleagues without an account. */
export function canManagePeople(permissions: TokenPermissions): boolean {
  return permissions.roleScopes.some((scope) => PEOPLE_ROLES.includes(scope.role));
}

/** The roles the caller may grant, in level order. */
export function grantableRoles(permissions: TokenPermissions): RoleType[] {
  const roles: RoleType[] = [...LEVELS, 'SYSTEM_ADMIN', 'GDPR_OFFICER'];
  return roles.filter((role) => permissions.canGrant(role));
}

/** The approval roles the caller holds in their own right; a role held only by delegation is left out. */
export function heldApprovalScopes(permissions: TokenPermissions): RoleScope[] {
  return permissions.heldScopes.filter((scope) => APPROVAL_ROLES.includes(scope.role));
}

/** The approval roles the caller may act in, their own and those held by delegation. */
export function approvalScopes(permissions: TokenPermissions): RoleScope[] {
  return permissions.roleScopes.filter((scope) => APPROVAL_ROLES.includes(scope.role));
}

/** Whether the caller may open the reviewer queue: an approval role of their own or by delegation. */
export function canReview(permissions: TokenPermissions): boolean {
  return approvalScopes(permissions).length > 0;
}

/** The approval levels up to the caller's highest approval role, in level order; empty without one. */
export function reviewableLevels(permissions: TokenPermissions): RoleType[] {
  const highest = Math.max(
    -1,
    ...approvalScopes(permissions).map((scope) => APPROVAL_ROLES.indexOf(scope.role)),
  );
  return APPROVAL_ROLES.slice(0, highest + 1);
}

/** Own scopes for a token issued before `held_scopes` existed: everything not named by a delegation. */
function withoutBorrowed(roleScopes: RoleScope[], delegated: DelegationScope[]): RoleScope[] {
  return roleScopes.filter(
    (scope) =>
      !delegated.some(
        (borrowed) =>
          borrowed.role === scope.role && borrowed.organizationId === scope.organizationId,
      ),
  );
}

/** The approval roles the caller may delegate, in level order and without repetition. */
export function delegatableRoles(permissions: TokenPermissions): RoleType[] {
  const held = heldApprovalScopes(permissions);
  return APPROVAL_ROLES.filter((role) => held.some((scope) => scope.role === role));
}

/** Organisations where the caller holds the role in their own right. */
export function delegatableOrganizations(permissions: TokenPermissions, role: RoleType): number[] {
  return heldApprovalScopes(permissions)
    .filter((scope) => scope.role === role)
    .map((scope) => scope.organizationId);
}

/** Whether the caller may open the delegations page. */
export function canDelegate(permissions: TokenPermissions): boolean {
  return heldApprovalScopes(permissions).length > 0;
}

function build(
  permissions: string[],
  roleScopes: RoleScope[],
  heldScopes: RoleScope[],
  delegations: DelegationScope[],
): TokenPermissions {
  const grantable = new Set(heldScopes.flatMap((scope) => GRANTS[scope.role] ?? []));
  const administrator = heldScopes.some((scope) => scope.role === 'SYSTEM_ADMIN');
  return {
    permissions,
    roleScopes,
    heldScopes,
    delegations,
    hasPermission: (permission) => permissions.includes(permission),
    canGrant: (role) => administrator || grantable.has(role),
  };
}

function payload(token: string | null | undefined): Record<string, unknown> | null {
  const part = token?.split('.')[1];
  if (!part) {
    return null;
  }
  try {
    const base64 = part.replace(/-/g, '+').replace(/_/g, '/');
    const padded = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), '=');
    const parsed: unknown = JSON.parse(utf8(atob(padded)));
    return parsed !== null && typeof parsed === 'object'
      ? (parsed as Record<string, unknown>)
      : null;
  } catch {
    return null;
  }
}

function utf8(binary: string): string {
  const bytes = Uint8Array.from([...binary].map((character) => character.charCodeAt(0)));
  return new TextDecoder().decode(bytes);
}

function strings(claim: unknown): string[] {
  if (Array.isArray(claim)) {
    return (claim as unknown[]).filter((value): value is string => typeof value === 'string');
  }
  return typeof claim === 'string' ? claim.split(' ').filter(Boolean) : [];
}

function scopes(values: string[]): RoleScope[] {
  const parsed: RoleScope[] = [];
  for (const value of values) {
    const [role, organizationId] = value.split(':');
    if (role && organizationId && Number.isInteger(Number(organizationId))) {
      parsed.push({ role: role as RoleType, organizationId: Number(organizationId) });
    }
  }
  return parsed;
}

function delegationScopes(values: string[]): DelegationScope[] {
  const parsed: DelegationScope[] = [];
  for (const value of values) {
    const [role, organizationId, delegatorId] = value.split(':');
    const numbers = [Number(organizationId), Number(delegatorId)];
    if (role && organizationId && delegatorId && numbers.every(Number.isInteger)) {
      parsed.push({
        role: role as RoleType,
        organizationId: numbers[0],
        delegatorId: numbers[1],
      });
    }
  }
  return parsed;
}
