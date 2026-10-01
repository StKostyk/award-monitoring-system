import { HttpStatusCode } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DOCUMENT,
  InjectionToken,
  OnDestroy,
  OnInit,
  computed,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatProgressBar } from '@angular/material/progress-bar';
import { TranslocoPipe } from '@jsverse/transloco';
import { Subscription } from 'rxjs';

import { problemStatus } from '../../../core/api/problem';
import { LanguageService } from '../../../core/i18n/language.service';
import { kyivDate, kyivDateTime } from '../../../shared/date-format';
import { AwardStatusView, AwardsService, FINAL_REQUEST_STATUSES } from '../awards.service';

/** How often an open award page reloads the review status. */
export const STATUS_POLL_MS = new InjectionToken<number>('STATUS_POLL_MS', {
  factory: () => 60_000,
});

/** Why the status cannot be shown: a retry may help (`failed`) or cannot (`gone`). */
export type StatusProblem = 'failed' | 'gone';

/**
 * The review timeline of a submitted award, reloaded while the page is visible and the request is not final.
 */
@Component({
  selector: 'app-award-status',
  imports: [MatButton, MatProgressBar, TranslocoPipe],
  templateUrl: './award-status.component.html',
  styleUrl: './award-status.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[hidden]': 'withoutRequest()' },
})
export class AwardStatusComponent implements OnInit, OnDestroy {
  private readonly service = inject(AwardsService);
  private readonly language = inject(LanguageService);
  private readonly document = inject(DOCUMENT);
  private readonly interval = inject(STATUS_POLL_MS);

  readonly awardId = input.required<number>();
  /** Emits the new status whenever a reload finds the request changed. */
  readonly changed = output<AwardStatusView>();

  readonly view = signal<AwardStatusView | null>(null);
  readonly loading = signal(false);
  readonly problem = signal<StatusProblem | null>(null);
  /** How many changes were announced; the live region text alternates so every change is read out. */
  readonly announcements = signal(0);
  readonly withoutRequest = computed(() => {
    const view = this.view();
    return view !== null && view.requestStatus === null;
  });
  readonly pendingReview = computed(() => {
    const status = this.view()?.requestStatus;
    return !!status && !FINAL_REQUEST_STATUSES.includes(status);
  });

  private timer: ReturnType<typeof setTimeout> | undefined;
  private request: Subscription | undefined;
  private stopped = false;
  private readonly visibilityChanged = (): void => this.onVisibilityChange();

  ngOnInit(): void {
    this.document.addEventListener('visibilitychange', this.visibilityChanged);
    this.load();
  }

  ngOnDestroy(): void {
    this.stopped = true;
    this.cancel();
    this.request?.unsubscribe();
    this.document.removeEventListener('visibilitychange', this.visibilityChanged);
  }

  /** Loads the status now. */
  load(): void {
    this.cancel();
    this.request?.unsubscribe();
    this.loading.set(true);
    this.request = this.service.status(this.awardId()).subscribe({
      next: (view) => {
        const previous = this.view();
        this.view.set(view);
        this.loading.set(false);
        this.problem.set(null);
        if (previous && changed(previous, view)) {
          this.announcements.update((count) => count + 1);
          this.changed.emit(view);
        }
        this.schedule();
      },
      error: (error: unknown) => {
        this.loading.set(false);
        const status = problemStatus(error);
        if (status === HttpStatusCode.NotFound || status === HttpStatusCode.Forbidden) {
          this.problem.set('gone');
          this.stopped = true;
        } else {
          this.problem.set('failed');
          this.schedule();
        }
      },
    });
  }

  date(value: string): string {
    return kyivDate(value, this.language.current());
  }

  time(value: string): string {
    return kyivDateTime(value, this.language.current());
  }

  private schedule(): void {
    if (this.stopped || !this.pending() || this.document.visibilityState === 'hidden') {
      return;
    }
    this.timer = setTimeout(() => this.load(), this.interval);
  }

  private pending(): boolean {
    const status = this.view()?.requestStatus;
    return this.view() === null || (!!status && !FINAL_REQUEST_STATUSES.includes(status));
  }

  private cancel(): void {
    clearTimeout(this.timer);
    this.timer = undefined;
  }

  private onVisibilityChange(): void {
    if (this.document.visibilityState === 'hidden') {
      this.cancel();
    } else if (!this.stopped && this.pending() && !this.loading()) {
      this.load();
    }
  }
}

function changed(before: AwardStatusView, after: AwardStatusView): boolean {
  return (
    before.requestStatus !== after.requestStatus ||
    before.currentLevel !== after.currentLevel ||
    before.decisions.length !== after.decisions.length ||
    before.overdue !== after.overdue
  );
}
