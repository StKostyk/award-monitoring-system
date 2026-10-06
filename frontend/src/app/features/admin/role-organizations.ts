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

/** Statuses the directory can list; unverified and deleted accounts never appear in it. */
export const STATUSES: AccountStatus[] = ['ACTIVE', 'INACTIVE', 'SUSPENDED', 'RETIRED', 'MEMORIAL'];

/** Organisation types an assignment of the role may point at; the server has the final say. */
export function organizationTypesFor(role: RoleType): OrganizationType[] {
  return TYPES[role];
}
