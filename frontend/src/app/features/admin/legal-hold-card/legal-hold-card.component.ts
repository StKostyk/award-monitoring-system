import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatCard, MatCardContent, MatCardHeader, MatCardTitle } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatProgressBar } from '@angular/material/progress-bar';
import { TranslocoPipe } from '@jsverse/transloco';

import { problemType } from '../../../core/api/problem';
import { LanguageService } from '../../../core/i18n/language.service';
import { kyivDateTime } from '../../../shared/date-format';
import {
  LegalHoldDialogComponent,
  LegalHoldDialogData,
  LegalHoldDialogResult,
} from '../legal-hold-dialog/legal-hold-dialog.component';
import { LegalHold, LegalHoldOverview, LegalHoldsService } from '../legal-holds.service';

/** The legal holds of one account, with placing and lifting, for holders of `legal-hold:manage`. */
@Component({
  selector: 'app-legal-hold-card',
  imports: [
    MatCard,
    MatCardHeader,
    MatCardTitle,
    MatCardContent,
    MatButton,
    MatProgressBar,
    TranslocoPipe,
  ],
  templateUrl: './legal-hold-card.component.html',
  styleUrl: './legal-hold-card.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LegalHoldCardComponent implements OnInit {
  private readonly api = inject(LegalHoldsService);
  private readonly dialog = inject(MatDialog);
  private readonly language = inject(LanguageService);

  readonly userId = input.required<number>();
  readonly name = input.required<string>();
  readonly erased = input(false);

  readonly overview = signal<LegalHoldOverview | null>(null);
  readonly loading = signal(false);
  readonly problem = signal<string | null>(null);
  readonly message = signal<string | null>(null);
  readonly active = computed(() => this.overview()?.holds.find((hold) => !hold.liftedAt) ?? null);
  readonly history = computed(() => this.overview()?.holds.filter((hold) => hold.liftedAt) ?? []);

  ngOnInit(): void {
    this.load();
  }

  place(): void {
    this.open({
      mode: 'place',
      name: this.name(),
      submit: (reason) => this.api.place(this.userId(), reason),
    });
  }

  lift(hold: LegalHold): void {
    this.open({
      mode: 'lift',
      name: this.name(),
      submit: (reason) => this.api.lift(this.userId(), hold.id, reason),
    });
  }

  moment(value: string): string {
    return kyivDateTime(value, this.language.current());
  }

  private open(data: LegalHoldDialogData): void {
    this.dialog
      .open<LegalHoldDialogComponent, LegalHoldDialogData, LegalHoldDialogResult>(
        LegalHoldDialogComponent,
        { data, width: '520px' },
      )
      .afterClosed()
      .subscribe((result) => {
        if (result) {
          this.message.set(
            result === 'stale'
              ? null
              : `admin.detail.legalHold.${data.mode === 'place' ? 'placed' : 'lifted'}`,
          );
          this.load();
        }
      });
  }

  private load(): void {
    this.loading.set(true);
    this.api.list(this.userId()).subscribe({
      next: (overview) => {
        this.loading.set(false);
        this.problem.set(null);
        this.overview.set(overview);
      },
      error: (error: unknown) => {
        this.loading.set(false);
        this.problem.set(problemType(error) === 'network' ? 'network' : 'load');
      },
    });
  }
}
