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

/** Roles ordered by level; `SYSTEM_ADMIN` grants everything and `GDPR_OFFICER` grants nothing. */
const LEVELS: RoleType[] = ['EMPLOYEE', 'FACULTY_SECRETARY', 'DEAN', 'RECTOR_SECRETARY', 'RECTOR'];

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

/** Whether the caller may open the user directory. */
export function canReadDirectory(permissions: TokenPermissions): boolean {
  return DIRECTORY_PERMISSIONS.some((permission) => permissions.hasPermission(permission));
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
  const level = heldScopes.reduce(
    (highest, scope) => Math.max(highest, LEVELS.indexOf(scope.role)),
    -1,
  );
  const administrator = heldScopes.some((scope) => scope.role === 'SYSTEM_ADMIN');
  return {
    permissions,
    roleScopes,
    heldScopes,
    delegations,
    hasPermission: (permission) => permissions.includes(permission),
    canGrant: (role) => administrator || (LEVELS.includes(role) && LEVELS.indexOf(role) < level),
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
