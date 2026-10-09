import { Injectable, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatDatepickerIntl } from '@angular/material/datepicker';
import { TranslocoService } from '@jsverse/transloco';

/** Datepicker labels in the active language. */
@Injectable()
export class TranslatedDatepickerIntl extends MatDatepickerIntl {
  private readonly transloco = inject(TranslocoService);

  constructor() {
    super();
    this.transloco
      .selectTranslate('app.datepicker.open')
      .pipe(takeUntilDestroyed())
      .subscribe((label: string) => this.relabel(label));
  }

  private relabel(open: string): void {
    this.openCalendarLabel = open;
    this.closeCalendarLabel = this.transloco.translate('app.datepicker.close');
    this.calendarLabel = this.transloco.translate('app.datepicker.calendar');
    this.prevMonthLabel = this.transloco.translate('app.datepicker.prevMonth');
    this.nextMonthLabel = this.transloco.translate('app.datepicker.nextMonth');
    this.prevYearLabel = this.transloco.translate('app.datepicker.prevYear');
    this.nextYearLabel = this.transloco.translate('app.datepicker.nextYear');
    this.prevMultiYearLabel = this.transloco.translate('app.datepicker.prevYears');
    this.nextMultiYearLabel = this.transloco.translate('app.datepicker.nextYears');
    this.switchToMonthViewLabel = this.transloco.translate('app.datepicker.chooseDate');
    this.switchToMultiYearViewLabel = this.transloco.translate('app.datepicker.chooseYear');
    this.changes.next();
  }
}
