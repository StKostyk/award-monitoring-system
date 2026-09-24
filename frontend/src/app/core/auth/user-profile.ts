export type RoleType =
  | 'EMPLOYEE'
  | 'FACULTY_SECRETARY'
  | 'DEAN'
  | 'RECTOR_SECRETARY'
  | 'RECTOR'
  | 'SYSTEM_ADMIN'
  | 'GDPR_OFFICER';

export type OrganizationType = 'UNIVERSITY' | 'COLLEGE' | 'FACULTY' | 'SPECIALITY' | 'DEPARTMENT';

export type AccountStatus =
  | 'PENDING'
  | 'ACTIVE'
  | 'INACTIVE'
  | 'SUSPENDED'
  | 'RETIRED'
  | 'MEMORIAL'
  | 'DELETED';

export interface OrganizationRef {
  id: number;
  name: string;
  nameUk: string | null;
  code: string | null;
  type: OrganizationType;
}

export interface RoleAssignment {
  id: number;
  role: RoleType;
  organization: OrganizationRef;
  validFrom: string;
  validTo: string | null;
}

export interface UserProfile {
  id: number;
  email: string;
  firstName: string;
  lastName: string;
  roles: RoleAssignment[];
  organization: OrganizationRef;
  status: AccountStatus;
  createdAt: string;
  lastLoginAt: string | null;
  membershipConfirmed: boolean;
}
