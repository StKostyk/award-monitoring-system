import { CdkConnectedOverlay, ConnectedPosition } from '@angular/cdk/overlay';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  input,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

import { LanguageService } from '../../../../core/i18n/language.service';
import { organizationName } from '../../../../shared/organization-name';
import {
  Achievement,
  AchievementsService,
  NO_ACHIEVEMENT_FILTERS,
} from '../../../achievements/achievements.service';
import { awardTitle } from '../../../awards/awards.service';
import { UnitAnalytics } from '../unit-analytics.service';

const PODIUM = 3;
/** How many of a person's latest public awards the card lists. */
export const LATEST_AWARDS = 3;

const CARD_POSITIONS: ConnectedPosition[] = [
  { originX: 'center', originY: 'bottom', overlayX: 'center', overlayY: 'top', offsetY: 6 },
  { originX: 'center', originY: 'top', overlayX: 'center', overlayY: 'bottom', offsetY: -6 },
];

type Person = UnitAnalytics['topPerformers']['people'][number];

interface Anchor {
  userId: number;
  origin: HTMLElement;
}

/** The latest awards of a person as the card shows them: still loading, or the titles with their year. */
export type LatestAwards = 'loading' | { title: string; year: string }[];

/**
 * W2 A: the people with the most awards who agreed to be named, the first three on a podium and the rest ranked.
 * Every person links to their portfolio; hover or focus shows a card with their latest public awards.
 */
@Component({
  selector: 'app-top-performers',
  imports: [CdkConnectedOverlay, RouterLink, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './top-performers.component.html',
  styleUrl: './top-performers.component.scss',
})
export class TopPerformersComponent {
  private readonly language = inject(LanguageService);
  private readonly achievements = inject(AchievementsService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly latest = signal<ReadonlyMap<number, Achievement[] | 'loading'>>(new Map());

  readonly topPerformers = input.required<UnitAnalytics['topPerformers']>();

  protected readonly positions = CARD_POSITIONS;
  protected readonly active = signal<Anchor | null>(null);
  private readonly focused = signal<Anchor | null>(null);
  protected readonly people = computed(() => {
    const language = this.language.current();
    return this.topPerformers().people.map((person, index) => ({
      ...person,
      rank: index + 1,
      initials: initials(person.lastName, person.firstName),
      departmentName: person.department ? organizationName(person.department, language) : null,
    }));
  });
  protected readonly podium = computed(() => this.people().slice(0, PODIUM));
  protected readonly rest = computed(() => this.people().slice(PODIUM));
  protected readonly ranked = computed(() => this.people().length > 1);
  protected readonly card = computed(() => {
    const active = this.active();
    const person = active && this.people().find((entry) => entry.userId === active.userId);
    if (!active || !person || !active.origin.isConnected) {
      return null;
    }
    const language = this.language.current();
    const awards = this.latest().get(person.userId) ?? [];
    const latest: LatestAwards =
      awards === 'loading'
        ? 'loading'
        : awards.map((award) => ({
            title: awardTitle(award, language),
            year: award.awardDate.slice(0, 4),
          }));
    return { person, origin: active.origin, latest };
  });

  /**
   * Shows the card of a person beside their row and loads their latest public awards once.
   *
   * @param person the person
   * @param origin the row the card points at
   */
  protected show(person: Person, origin: HTMLElement): void {
    this.active.set({ userId: person.userId, origin });
    this.load(person.userId);
  }

  /**
   * Shows the card of the focused person and keeps it while the pointer passes over other rows.
   *
   * @param person the person
   * @param origin the focused link
   */
  protected focus(person: Person, origin: HTMLElement): void {
    this.focused.set({ userId: person.userId, origin });
    this.show(person, origin);
  }

  /**
   * Hides the card of a person whose link lost the focus.
   *
   * @param person the person
   */
  protected blur(person: Person): void {
    this.focused.set(null);
    this.hide(person);
  }

  /**
   * Hides the card when it belongs to the row that was left, or goes back to the focused person's card.
   *
   * @param person the person whose row was left
   */
  protected hide(person: Person): void {
    if (this.active()?.userId === person.userId) {
      const focused = this.focused();
      this.active.set(focused?.userId === person.userId ? null : focused);
    }
  }

  protected close(): void {
    this.active.set(null);
  }

  private load(userId: number): void {
    if (this.latest().has(userId)) {
      return;
    }
    this.remember(userId, 'loading');
    this.achievements
      .list(
        {
          filters: { ...NO_ACHIEVEMENT_FILTERS, person: userId },
          page: 0,
          size: LATEST_AWARDS,
        },
        'public',
      )
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (page) => this.remember(userId, page.content.slice(0, LATEST_AWARDS)),
        error: () => this.forget(userId),
      });
  }

  private forget(userId: number): void {
    this.latest.update((latest) => {
      const next = new Map(latest);
      next.delete(userId);
      return next;
    });
  }

  private remember(userId: number, awards: Achievement[] | 'loading'): void {
    this.latest.update((latest) => new Map(latest).set(userId, awards));
  }
}

function initials(...names: string[]): string {
  return names
    .map((name) => name.trim().charAt(0))
    .join('')
    .toLocaleUpperCase();
}
