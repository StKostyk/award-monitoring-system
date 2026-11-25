import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { environment } from '../../../environments/environment';

/** A getting-started step; the order of the API is the order on screen. */
export type OnboardingStepKey =
  | 'NOTICE'
  | 'PROFILE'
  | 'FIRST_AWARD'
  | 'VISIBILITY'
  | 'NOTIFICATIONS'
  | 'REVIEW_QUEUE'
  | 'DELEGATE'
  | 'IMPORT';

/** One step of the caller's roles; a derived step is decided by the data and never stored. */
export interface OnboardingStep {
  key: OnboardingStepKey;
  done: boolean;
  derived: boolean;
}

/** The caller's steps, whether the checklist was hidden and whether the tour was seen. */
export interface OnboardingState {
  steps: OnboardingStep[];
  dismissed: boolean;
  tourSeen: boolean;
}

/** The stored progress, replaced as a whole. */
export interface OnboardingUpdate {
  done: OnboardingStepKey[];
  dismissed: boolean;
  tourSeen: boolean;
}

/** Where a step's button leads: a route, with an optional query or fragment. */
export interface StepLink {
  path: string;
  queryParams?: Record<string, string>;
  fragment?: string;
}

/** The page each step opens. */
export const STEP_LINKS: Record<OnboardingStepKey, StepLink> = {
  NOTICE: { path: '/privacy-notice' },
  PROFILE: { path: '/profile' },
  FIRST_AWARD: { path: '/awards/new' },
  VISIBILITY: { path: '/privacy', fragment: 'choices' },
  NOTIFICATIONS: { path: '/profile', queryParams: { section: 'notifications' } },
  REVIEW_QUEUE: { path: '/reviews' },
  DELEGATE: { path: '/delegations' },
  IMPORT: { path: '/admin/import' },
};

/** The getting-started progress of the signed-in account, read fresh on every page that shows it. */
@Injectable({ providedIn: 'root' })
export class OnboardingService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/users/me/onboarding`;

  /** The last state read or saved; null before the first read or after a failed one. */
  readonly state = signal<OnboardingState | null>(null);

  /** Reads the caller's steps; a failure leaves no state, so nothing is shown. */
  async load(): Promise<void> {
    try {
      this.state.set(await firstValueFrom(this.http.get<OnboardingState>(this.url)));
    } catch {
      this.state.set(null);
    }
  }

  /**
   * Stores a step as done; a derived step, one already done or one outside the caller's steps changes nothing.
   *
   * @param key the step whose page was opened
   */
  markDone(key: OnboardingStepKey): Promise<void> {
    const state = this.state();
    const step = state?.steps.find((candidate) => candidate.key === key);
    if (!state || !step || step.derived || step.done) {
      return Promise.resolve();
    }
    return this.save({ ...this.update(state), done: [...storedDone(state), key] });
  }

  /** Hides the checklist for good. */
  dismiss(): Promise<void> {
    const state = this.state();
    return state ? this.save({ ...this.update(state), dismissed: true }) : Promise.resolve();
  }

  /** Records that the tour was shown to its end or left. */
  markTourSeen(): Promise<void> {
    const state = this.state();
    return state && !state.tourSeen
      ? this.save({ ...this.update(state), tourSeen: true })
      : Promise.resolve();
  }

  private update(state: OnboardingState): OnboardingUpdate {
    return { done: storedDone(state), dismissed: state.dismissed, tourSeen: state.tourSeen };
  }

  private async save(update: OnboardingUpdate): Promise<void> {
    try {
      this.state.set(await firstValueFrom(this.http.put<OnboardingState>(this.url, update)));
    } catch {
      await this.load();
    }
  }
}

/** The stored steps that are done: the ones the client sends back on a save. */
export function storedDone(state: OnboardingState): OnboardingStepKey[] {
  return state.steps.filter((step) => step.done && !step.derived).map((step) => step.key);
}
