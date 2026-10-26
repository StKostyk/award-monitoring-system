import {
  ChangeDetectionStrategy,
  Component,
  effect,
  input,
  output,
  signal,
  untracked,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatIconButton } from '@angular/material/button';
import { MatFormField, MatLabel, MatSuffix } from '@angular/material/form-field';
import { MatIcon } from '@angular/material/icon';
import { MatInput } from '@angular/material/input';
import { TranslocoPipe } from '@jsverse/transloco';
import { Subject, debounce, filter, map, of, timer } from 'rxjs';

/** Milliseconds of quiet typing before a search starts. */
export const SEARCH_DEBOUNCE = 300;
/** Shorter search texts list as if there were none. */
export const SEARCH_MIN_LENGTH = 2;
/** The longest search text the API accepts. */
export const SEARCH_MAX_LENGTH = 200;

/**
 * The search text a list is filtered by.
 *
 * @param text the typed text
 * @returns the trimmed text, or null when it is too short to search by or longer than the API accepts
 */
export function searchTerm(text: string | null | undefined): string | null {
  const term = (text ?? '').trim();
  return term.length < SEARCH_MIN_LENGTH || term.length > SEARCH_MAX_LENGTH ? null : term;
}

@Component({
  selector: 'app-search-field',
  imports: [
    FormsModule,
    MatFormField,
    MatLabel,
    MatInput,
    MatSuffix,
    MatIconButton,
    MatIcon,
    TranslocoPipe,
  ],
  templateUrl: './search-field.component.html',
  styleUrl: './search-field.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SearchFieldComponent {
  /** The translation key of the field label. */
  readonly label = input.required<string>();
  /** The search the list currently shows; null when it shows no search. */
  readonly value = input<string | null>(null);
  /** The new search text after a pause in typing, or null when the search is cleared. */
  readonly search = output<string | null>();

  protected readonly maxLength = SEARCH_MAX_LENGTH;
  protected readonly text = signal('');
  private readonly typed = new Subject<string>();

  constructor() {
    effect(() => {
      const applied = this.value();
      if (searchTerm(untracked(this.text)) !== applied) {
        this.text.set(applied ?? '');
      }
    });
    this.typed
      .pipe(
        debounce((text) => (text === '' ? of(text) : timer(SEARCH_DEBOUNCE))),
        map(searchTerm),
        filter((term) => term !== this.value()),
        takeUntilDestroyed(),
      )
      .subscribe((term) => this.search.emit(term));
  }

  type(text: string): void {
    this.text.set(text);
    this.typed.next(text);
  }

  clear(): void {
    this.type('');
  }
}
