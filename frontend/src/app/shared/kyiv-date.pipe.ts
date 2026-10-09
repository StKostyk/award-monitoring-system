import { Pipe, PipeTransform, inject } from '@angular/core';

import { LanguageService } from '../core/i18n/language.service';
import { kyivDate } from './date-format';

/** A day in Kyiv, day first, in the interface language; «—» when there is none. */
@Pipe({ name: 'kyivDate', pure: false })
export class KyivDatePipe implements PipeTransform {
  private readonly language = inject(LanguageService);
  private last: { value: string; language: string; shown: string } | null = null;

  transform(value: string | null | undefined): string {
    if (!value) {
      return '—';
    }
    const language = this.language.current();
    if (this.last?.value !== value || this.last.language !== language) {
      this.last = { value, language, shown: kyivDate(value, language) };
    }
    return this.last.shown;
  }
}
