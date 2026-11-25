import { BreakpointObserver } from '@angular/cdk/layout';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  afterNextRender,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { map } from 'rxjs';

import { LanguageService } from '../../../core/i18n/language.service';
import { monthNames } from '../../../shared/date-format';
import { IconComponent } from '../../../shared/icons/icon.component';
import { pluralForm } from '../../../shared/plural';
import {
  RECOGNITION_LEVELS,
  RecognitionLevel,
  awardTitle,
  categoryName,
} from '../../awards/awards.service';
import { TimelineEntry } from '../analytics.service';

const WIDE_QUERY = '(min-width: 960px)';
const STOP_WIDTH = 150;
const MIN_PER_ROW = 2;
const MAX_PER_ROW = 6;
const DEFAULT_PER_ROW = 4;
const NATIONAL_AND_UP: readonly RecognitionLevel[] = RECOGNITION_LEVELS.slice(
  RECOGNITION_LEVELS.indexOf('NATIONAL'),
);

/** Which awards the feed shows. */
export type TimelineFilter = 'all' | 'national';

interface ShownEntry extends TimelineEntry {
  year: string;
  day: string;
  month: string;
  shownTitle: string;
  categoryName: string;
}

/**
 * Every counted award of the person: a road from the first award to today at 960 px and wider, a feed grouped by
 * year below; every milestone and row opens the award.
 */
@Component({
  selector: 'app-timeline-chart',
  imports: [IconComponent, RouterLink, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './timeline-chart.component.html',
  styleUrl: './timeline-chart.component.scss',
})
export class TimelineChartComponent {
  private readonly language = inject(LanguageService);

  readonly timeline = input.required<TimelineEntry[]>();
  readonly truncated = input(false);

  protected readonly wide = toSignal(
    inject(BreakpointObserver)
      .observe(WIDE_QUERY)
      .pipe(map((state) => state.matches)),
    { initialValue: false },
  );
  protected readonly perRow = signal(DEFAULT_PER_ROW);
  protected readonly filter = signal<TimelineFilter>('all');

  private readonly entries = computed<ShownEntry[]>(() => {
    const language = this.language.current();
    const months = monthNames(language);
    return this.timeline().map((entry) => ({
      ...entry,
      year: entry.awardDate.slice(0, 4),
      day: entry.awardDate.slice(8, 10),
      month: months[Number(entry.awardDate.slice(5, 7)) - 1],
      shownTitle: awardTitle(entry, language),
      categoryName: categoryName(entry.category, language),
    }));
  });

  protected readonly road = computed(() => {
    const perRow = this.perRow();
    const stops = [...this.entries()].reverse();
    const rows = Array.from({ length: Math.ceil(stops.length / perRow) }, (_, row) => ({
      row: row + 1,
      back: row % 2 === 1,
      stops: stops.slice(row * perRow, (row + 1) * perRow).map((stop, index) => ({
        ...stop,
        column: row % 2 === 1 ? perRow - index : index + 1,
      })),
    }));
    return { first: stops[0]?.year ?? '', rows };
  });

  protected readonly feed = computed(() => {
    const language = this.language.current();
    const national = this.filter() === 'national';
    const groups: { year: string; form: Intl.LDMLPluralRule; items: ShownEntry[] }[] = [];
    for (const entry of this.entries()) {
      if (national && !NATIONAL_AND_UP.includes(entry.level)) {
        continue;
      }
      const last = groups.at(-1);
      if (last?.year === entry.year) {
        last.items.push(entry);
      } else {
        groups.push({ year: entry.year, form: 'other', items: [entry] });
      }
    }
    return groups.map((group) => ({ ...group, form: pluralForm(group.items.length, language) }));
  });

  constructor() {
    const host = inject<ElementRef<HTMLElement>>(ElementRef);
    const destroyRef = inject(DestroyRef);
    afterNextRender(() => {
      if (typeof ResizeObserver === 'undefined') {
        return;
      }
      const observer = new ResizeObserver(([entry]) => {
        const fits = Math.floor(entry.contentRect.width / STOP_WIDTH);
        this.perRow.set(Math.min(MAX_PER_ROW, Math.max(MIN_PER_ROW, fits)));
      });
      observer.observe(host.nativeElement);
      destroyRef.onDestroy(() => observer.disconnect());
    });
  }
}
