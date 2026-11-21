import { RoleType } from '../../core/auth/user-profile';

/** What a delegate may and may not do in a borrowed role, as i18n keys under `delegations.capabilities`. */
export interface Capabilities {
  can: string[];
  cannot: string[];
}

/**
 * The award permissions each approval role holds, as in the backend permission matrix; only these travel with a
 * delegation (reading and approving awards).
 */
const DELEGABLE: Partial<Record<RoleType, string[]>> = {
  FACULTY_SECRETARY: ['readDepartment', 'approveLevel1'],
  DEAN: ['readDepartment', 'readFaculty', 'approveLevel1', 'approveLevel2'],
  RECTOR_SECRETARY: [
    'readDepartment',
    'readFaculty',
    'readAll',
    'approveLevel1',
    'approveLevel2',
    'approveLevel3',
  ],
  RECTOR: [
    'readDepartment',
    'readFaculty',
    'readAll',
    'approveLevel1',
    'approveLevel2',
    'approveLevel3',
    'approveFinal',
  ],
};

/** What stays with the delegator whatever the role: people, analytics and passing the role on. */
const KEPT = ['manageUsers', 'analytics', 'delegate'];

/**
 * The «can» and «cannot» lines of a delegated role.
 *
 * @param role the delegated role
 * @returns the i18n keys of both lists; a role that cannot be delegated can do nothing
 */
export function capabilitiesOf(role: RoleType): Capabilities {
  return {
    can: (DELEGABLE[role] ?? []).map((key) => `delegations.capabilities.can.${key}`),
    cannot: KEPT.map((key) => `delegations.capabilities.cannot.${key}`),
  };
}
