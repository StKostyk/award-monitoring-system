import { RoleType } from './user-profile';

/** One role the caller holds in one organisation, read from the `role_scopes` claim. */
export interface RoleScope {
  role: RoleType;
  organizationId: number;
}

/** What the current access token carries; used to hide controls, never to authorise. */
export interface TokenPermissions {
  permissions: string[];
  roleScopes: RoleScope[];
  hasPermission(permission: string): boolean;
  canGrant(role: RoleType): boolean;
}

/** Roles ordered by level; `SYSTEM_ADMIN` grants everything and `GDPR_OFFICER` grants nothing. */
const LEVELS: RoleType[] = ['EMPLOYEE', 'FACULTY_SECRETARY', 'DEAN', 'RECTOR_SECRETARY', 'RECTOR'];

/** Permissions that open the user directory. */
export const DIRECTORY_PERMISSIONS = ['user:read:scope', 'user:read:all'];

export const NO_PERMISSIONS: TokenPermissions = build([], []);

/** Reads the `permissions` and `role_scopes` claims of an access token; anything unreadable yields nothing. */
export function readPermissions(token: string | null | undefined): TokenPermissions {
  const claims = payload(token);
  if (!claims) {
    return NO_PERMISSIONS;
  }
  return build(strings(claims['permissions']), scopes(strings(claims['role_scopes'])));
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

function build(permissions: string[], roleScopes: RoleScope[]): TokenPermissions {
  const level = roleScopes.reduce(
    (highest, scope) => Math.max(highest, LEVELS.indexOf(scope.role)),
    -1,
  );
  const administrator = roleScopes.some((scope) => scope.role === 'SYSTEM_ADMIN');
  return {
    permissions,
    roleScopes,
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
    return parsed !== null && typeof parsed === 'object' ? (parsed as Record<string, unknown>) : null;
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
