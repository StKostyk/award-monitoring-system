import { OnboardingState } from './onboarding.service';

/** An employee's steps: the notice and the profile done, the profile stored. */
export function employeeState(overrides: Partial<OnboardingState> = {}): OnboardingState {
  return {
    steps: [
      { key: 'NOTICE', done: true, derived: true },
      { key: 'PROFILE', done: true, derived: false },
      { key: 'FIRST_AWARD', done: false, derived: true },
      { key: 'VISIBILITY', done: false, derived: false },
      { key: 'NOTIFICATIONS', done: false, derived: false },
    ],
    dismissed: false,
    tourSeen: false,
    ...overrides,
  };
}
