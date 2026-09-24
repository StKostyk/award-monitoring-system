import { AccountStatus, OrganizationType, RoleType } from '../../core/auth/user-profile';

const TYPES: Record<RoleType, OrganizationType[]> = {
  EMPLOYEE: ['DEPARTMENT'],
  FACULTY_SECRETARY: ['FACULTY', 'DEPARTMENT'],
  DEAN: ['FACULTY', 'COLLEGE'],
  RECTOR_SECRETARY: ['UNIVERSITY'],
  RECTOR: ['UNIVERSITY'],
  SYSTEM_ADMIN: ['UNIVERSITY'],
  GDPR_OFFICER: ['UNIVERSITY'],
};

export const ROLES: RoleType[] = [
  'EMPLOYEE',
  'FACULTY_SECRETARY',
  'DEAN',
  'RECTOR_SECRETARY',
  'RECTOR',
  'SYSTEM_ADMIN',
  'GDPR_OFFICER',
];

export const STATUSES: AccountStatus[] = [
  'PENDING',
  'ACTIVE',
  'INACTIVE',
  'SUSPENDED',
  'RETIRED',
  'MEMORIAL',
  'DELETED',
];

/** Organisation types an assignment of the role may point at; the server has the final say. */
export function organizationTypesFor(role: RoleType): OrganizationType[] {
  return TYPES[role];
}

/** The organisation name in the active language, falling back to the English one. */
export function organizationName(
  organization: { name: string; nameUk: string | null },
  language: string,
): string {
  return language === 'uk' && organization.nameUk ? organization.nameUk : organization.name;
}

/** Today as `YYYY-MM-DD`, the format the API uses for role validity. */
export function today(): string {
  const now = new Date();
  return new Date(now.getTime() - now.getTimezoneOffset() * 60_000).toISOString().substring(0, 10);
}
