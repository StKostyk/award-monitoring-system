import { Overlay, OverlayRef } from '@angular/cdk/overlay';
import { ComponentPortal } from '@angular/cdk/portal';
import {
  ChangeDetectionStrategy,
  Component,
  OnDestroy,
  OnInit,
  booleanAttribute,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import { MatButton } from '@angular/material/button';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

import { AuthService } from '../../../core/auth/auth.service';
import { canReadOwnAnalytics } from '../../../core/auth/permissions';
import { IconComponent } from '../../../shared/icons/icon.component';
import { IconName } from '../../../shared/icons/icons';
import { PaneComponent } from '../../../shared/layout/pane.component';
import { OnboardingTourComponent } from '../onboarding-tour/onboarding-tour.component';
import {
  OnboardingService,
  OnboardingStep,
  OnboardingStepKey,
  STEP_LINKS,
} from '../onboarding.service';

/** The icon of each step. */
const STEP_ICONS: Record<OnboardingStepKey, IconName> = {
  NOTICE: 'shield-check',
  PROFILE: 'user',
  FIRST_AWARD: 'medal',
  VISIBILITY: 'eye',
  NOTIFICATIONS: 'bell',
  REVIEW_QUEUE: 'clipboard-check',
  DELEGATE: 'users',
  IMPORT: 'upload',
};

/**
 * The getting-started card: the steps of the account's roles with a button to each page, «Сховати список» for good,
 * a line when everything is done (shown once, then the card is gone) and, on My dashboard, the spotlight tour. The
 * administrator and the data protection officer, who have no My dashboard, see it on their landing page.
 */
@Component({
  selector: 'app-onboarding-checklist',
  imports: [IconComponent, MatButton, PaneComponent, RouterLink, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './onboarding-checklist.component.html',
  styleUrl: './onboarding-checklist.component.scss',
})
export class OnboardingChecklistComponent implements OnInit, OnDestroy {
  /** True on My dashboard, the one page the tour knows. */
  readonly tour = input(false, { transform: booleanAttribute });
  /** True on a landing page other than My dashboard: the card shows only to accounts without My dashboard. */
  readonly landing = input(false, { transform: booleanAttribute });

  protected readonly links = STEP_LINKS;
  protected readonly icons = STEP_ICONS;
  protected readonly firstName = computed(() => this.auth.profile()?.firstName ?? '');
  protected readonly steps = computed(() => this.onboarding.state()?.steps ?? []);
  protected readonly doneCount = computed(() => this.steps().filter((step) => step.done).length);
  protected readonly allDone = computed(
    () => this.steps().length > 0 && this.doneCount() === this.steps().length,
  );
  protected readonly visible = computed(() => {
    const state = this.onboarding.state();
    return (
      this.shown() &&
      state !== null &&
      state.steps.length > 0 &&
      (!state.dismissed || this.finishedNow())
    );
  });

  private readonly auth = inject(AuthService);
  private readonly onboarding = inject(OnboardingService);
  private readonly finishedNow = signal(false);
  private readonly shown = signal(false);
  private readonly overlay = inject(Overlay);
  private tourRef: OverlayRef | null = null;

  async ngOnInit(): Promise<void> {
    if (this.landing() && canReadOwnAnalytics(this.auth.permissions())) {
      return;
    }
    await this.onboarding.load();
    this.shown.set(true);
    const state = this.onboarding.state();
    if (state && !state.dismissed && this.allDone()) {
      this.finishedNow.set(true);
      await this.onboarding.dismiss();
    }
  }

  open(step: OnboardingStep): void {
    void this.onboarding.markDone(step.key);
  }

  dismiss(): void {
    void this.onboarding.dismiss();
  }

  ngOnDestroy(): void {
    this.tourRef?.dispose();
  }

  startTour(): void {
    if (this.tourRef) {
      return;
    }
    this.tourRef = this.overlay.create();
    const tour = this.tourRef.attach(new ComponentPortal(OnboardingTourComponent));
    tour.instance.ended.subscribe(() => this.endTour());
  }

  endTour(): void {
    this.tourRef?.dispose();
    this.tourRef = null;
    void this.onboarding.markTourSeen();
  }
}
