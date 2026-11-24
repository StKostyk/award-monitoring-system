import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { MatButton } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatProgressBar } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { filter, of } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { commandCentreScope } from '../../core/auth/permissions';
import { LanguageService } from '../../core/i18n/language.service';
import { DashboardLayoutComponent } from '../../shared/dashboard/dashboard-layout.component';
import { DashboardWidgetDirective } from '../../shared/dashboard/dashboard-widget.directive';
import { kyivDate } from '../../shared/date-format';
import { EmptyStateComponent } from '../../shared/empty-state/empty-state.component';
import { PaneComponent } from '../../shared/layout/pane.component';
import { organizationName } from '../../shared/organization-name';
import { FocusCardsWidgetComponent } from '../../shared/widgets/focus-cards-widget.component';
import { TopWidgetComponent } from '../../shared/widgets/top-widget.component';
import { FocusCard, TopPerson } from '../../shared/widgets/widget-data';
import { AnalyticsPeriod } from '../analytics/analytics.service';
import {
  UnitAnalytics,
  UnitAnalyticsService,
  UnitComparisonRow,
} from '../analytics/units/unit-analytics.service';
import {
  UniversityAnalytics,
  UniversityAnalyticsService,
} from '../analytics/university/university-analytics.service';
import { Page, awardTitle } from '../awards/awards.service';
import {
  DecisionDialogComponent,
  DecisionDialogData,
  DecisionInput,
} from '../reviews/decision-dialog/decision-dialog.component';
import { HeldDecisionService } from '../reviews/held-decision.service';
import { NO_REVIEW_FILTERS, ReviewItem, ReviewsService } from '../reviews/reviews.service';
import { AttentionUnit, attentionUnits, facultyTop, unitTop } from './command-centre-view';
import { CommandCentreService, ReviewDay } from './command-centre.service';
import {
  ReminderDialogComponent,
  ReminderDialogData,
  ReminderResult,
} from './reminder-dialog/reminder-dialog.component';

/** How many requests of the queue the page lists. */
export const QUEUE_SIZE = 3;
const DEFAULT_PERIOD: AnalyticsPeriod = { from: null, to: null };

/**
 * «Командний центр»: the queue of the caller's level with today's decisions, the units that need attention and
 * the top of the faculty; a faculty secretary or dean may remind a department without awards.
 */
@Component({
  selector: 'app-command-centre',
  imports: [
    DashboardLayoutComponent,
    DashboardWidgetDirective,
    EmptyStateComponent,
    FocusCardsWidgetComponent,
    MatButton,
    MatProgressBar,
    PaneComponent,
    RouterLink,
    TopWidgetComponent,
    TranslocoPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './command-centre.component.html',
  styleUrl: './command-centre.component.scss',
})
export class CommandCentreComponent {
  private readonly auth = inject(AuthService);
  private readonly reviews = inject(ReviewsService);
  private readonly service = inject(CommandCentreService);
  private readonly unitAnalytics = inject(UnitAnalyticsService);
  private readonly universityAnalytics = inject(UniversityAnalyticsService);
  private readonly held = inject(HeldDecisionService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly language = inject(LanguageService);
  private readonly transloco = inject(TranslocoService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly translationLoaded = toSignal(this.transloco.selectTranslation());

  /** The queue level and the faculty; the guard lets nobody in without one. */
  readonly scope = commandCentreScope(this.auth.permissions());
  /** Whether the page shows the faculties of the university, without the reminder. */
  readonly university = this.scope?.facultyId === null;

  private readonly queue = signal<Page<ReviewItem> | null>(null);
  private readonly day = signal<ReviewDay | null>(null);
  private readonly faculty = signal<UnitAnalytics | null>(null);
  private readonly universityData = signal<UniversityAnalytics | null>(null);
  private readonly reminded = signal<ReadonlyMap<number, string>>(new Map());
  readonly loading = signal(true);
  readonly failed = signal(false);

  /** The requests listed, without those whose decision is held for «Скасувати». */
  readonly items = computed(() => {
    const heldIds = this.held.heldIds();
    return (this.queue()?.content ?? [])
      .filter((item) => !heldIds.has(item.awardId))
      .slice(0, QUEUE_SIZE);
  });

  /** Requests waiting for the caller's level, less those decided and held. */
  readonly waiting = computed(() => {
    const queue = this.queue();
    if (!queue) {
      return null;
    }
    const heldIds = this.held.heldIds();
    const heldHere = queue.content.filter((item) => heldIds.has(item.awardId)).length;
    return Math.max(0, queue.totalElements - heldHere);
  });

  readonly decided = computed(() => this.day()?.decided ?? null);

  protected readonly unitName = computed(() => {
    const unit = this.university ? this.universityData()?.unit : this.faculty()?.unit;
    return unit ? organizationName(unit, this.language.current()) : '';
  });

  private readonly rows = computed<UnitComparisonRow[]>(
    () => (this.university ? this.universityData()?.units : this.faculty()?.units) ?? [],
  );

  private readonly period = computed(
    () => (this.university ? this.universityData()?.period : this.faculty()?.period) ?? null,
  );

  private readonly attention = computed(() => attentionUnits(this.rows()));

  /** The cards of the units that need attention, in the interface language. */
  readonly cards = computed<FocusCard[]>(() => {
    this.translationLoaded();
    const language = this.language.current();
    const period = this.period();
    const reminded = this.reminded();
    if (!period) {
      return [];
    }
    return this.attention().map((unit) => ({
      id: `unit-${unit.unit.id}`,
      title: organizationName(unit.unit, language),
      line: this.line(unit, period),
      action: this.action(unit, reminded.get(unit.unit.id) ?? unit.lastReminderAt, language),
    }));
  });

  readonly top = computed<TopPerson[]>(() => {
    const language = this.language.current();
    if (this.university) {
      return unitTop(this.universityData()?.units ?? [], language);
    }
    const faculty = this.faculty();
    return faculty ? facultyTop(faculty) : [];
  });

  constructor() {
    this.held.sent.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.loadQueue();
      this.loadDay();
    });
    this.held.returned
      .pipe(
        filter((returned) => returned.problem !== null),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(() => this.loadQueue());
    this.load();
  }

  /** Reads everything again after a failure. */
  load(): void {
    this.failed.set(false);
    this.loadQueue();
    this.loadDay();
    this.loadUnits();
  }

  /** Whether the caller may decide the request now: nobody else works on it. */
  canDecide(item: ReviewItem): boolean {
    return item.reviewer === null || String(item.reviewer.id) === this.auth.userId();
  }

  title(item: ReviewItem): string {
    return awardTitle(item, this.language.current());
  }

  recipient(item: ReviewItem): string {
    const unit = item.recipient.organization;
    return unit ? organizationName(unit, this.language.current()) : item.owner.name;
  }

  /**
   * Opens the decision dialog as the review queue does; a confirmed decision is held for «Скасувати» and then sent.
   *
   * @param item the request
   * @param decision approve or return
   */
  decide(item: ReviewItem, decision: 'APPROVE' | 'RETURN'): void {
    if (!this.canDecide(item)) {
      return;
    }
    const data: DecisionDialogData<DecisionInput> = {
      decision,
      target: null,
      documents: item.documentCount,
      draft: String(item.awardId),
      submit: (entered: DecisionInput) => of(entered),
    };
    this.dialog
      .open<DecisionDialogComponent, DecisionDialogData<DecisionInput>, DecisionInput>(
        DecisionDialogComponent,
        { data, width: '480px' },
      )
      .afterClosed()
      .pipe(filter((entered): entered is DecisionInput => !!entered))
      .subscribe((entered) =>
        this.held.hold(item.awardId, {
          decision,
          requestVersion: item.requestVersion,
          ...entered,
        }),
      );
  }

  /**
   * Opens «Нагадати завідувачу» for a department card; the card shows the reminder once the dialog reports one.
   *
   * @param command the card's command, the department id
   */
  remind(command: string): void {
    const unit = this.attention().find((each) => String(each.unit.id) === command);
    const period = this.period();
    if (!unit || !period || this.university) {
      return;
    }
    this.dialog
      .open<ReminderDialogComponent, ReminderDialogData, ReminderResult>(ReminderDialogComponent, {
        data: { unit: unit.unit, year: period.to },
        width: '480px',
      })
      .afterClosed()
      .pipe(filter((result): result is ReminderResult => !!result))
      .subscribe((result) => {
        this.reminded.update((reminded) => new Map(reminded).set(unit.unit.id, result.sentAt));
        this.snackBar.open(
          this.transloco.translate('commandCentre.units.reminded', {
            date: kyivDate(result.sentAt, this.language.current()),
          }),
          undefined,
          { duration: 5000 },
        );
      });
  }

  private loadQueue(): void {
    const level = this.scope?.level ?? null;
    this.reviews
      .list({ ...NO_REVIEW_FILTERS, level }, 0, QUEUE_SIZE)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: (page) => this.queue.set(page), error: () => this.failed.set(true) });
  }

  private loadDay(): void {
    this.service
      .reviewDay()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: (day) => this.day.set(day), error: () => this.failed.set(true) });
  }

  private loadUnits(): void {
    this.loading.set(true);
    const done = {
      complete: () => this.loading.set(false),
      error: () => {
        this.loading.set(false);
        this.failed.set(true);
      },
    };
    const facultyId = this.scope?.facultyId ?? null;
    if (facultyId === null) {
      this.universityAnalytics
        .read(DEFAULT_PERIOD)
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({ next: (data) => this.universityData.set(data), ...done });
      return;
    }
    this.unitAnalytics
      .read(facultyId, DEFAULT_PERIOD)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: (data) => this.faculty.set(data), ...done });
  }

  private line(unit: AttentionUnit, period: { from: number; to: number }): string {
    const now =
      unit.attention === 'NO_AWARDS'
        ? this.transloco.translate('commandCentre.units.none', { year: period.to })
        : this.transloco.translate('commandCentre.units.decline', {
            current: unit.thisYear,
            year: period.to,
            previous: unit.lastYear,
            lastYear: period.to - 1,
          });
    if (period.from >= period.to) {
      return now;
    }
    const earlier = this.transloco.translate('commandCentre.units.earlier', {
      from: period.from,
      to: period.to - 1,
      count: unit.earlier,
    });
    return `${now} · ${earlier}`;
  }

  private action(
    unit: AttentionUnit,
    remindedAt: string | null,
    language: string,
  ): FocusCard['action'] {
    if (this.university) {
      return null;
    }
    if (unit.attention === 'DECLINE') {
      return {
        label: this.transloco.translate('commandCentre.units.analytics'),
        link: ['/analytics/units', unit.unit.id],
      };
    }
    if (remindedAt) {
      return {
        label: this.transloco.translate('commandCentre.units.reminded', {
          date: kyivDate(remindedAt, language),
        }),
        command: String(unit.unit.id),
        disabled: true,
      };
    }
    return {
      label: this.transloco.translate('commandCentre.units.remind'),
      command: String(unit.unit.id),
    };
  }
}
