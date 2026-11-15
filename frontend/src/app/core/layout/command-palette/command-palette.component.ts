import { NgTemplateOutlet } from '@angular/common';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatDialogRef } from '@angular/material/dialog';
import { Router } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { Observable, Subject, catchError, debounceTime, map, of, switchMap } from 'rxjs';

import {
  Award,
  AwardsService,
  NO_FILTERS,
  awardTitle,
} from '../../../features/awards/awards.service';
import { IconComponent } from '../../../shared/icons/icon.component';
import { IconName } from '../../../shared/icons/icons';
import { AuthService } from '../../auth/auth.service';
import { canReadOwnAwards } from '../../auth/permissions';
import { LanguageService } from '../../i18n/language.service';
import { navEntriesFor } from '../nav-entries';

/** Characters a search needs before it filters sections and asks for awards. */
export const PALETTE_MIN_QUERY = 2;

/** Pause after the last keystroke before awards are asked for, in milliseconds. */
export const PALETTE_DEBOUNCE_MS = 250;

/** Awards the palette shows at most. */
export const PALETTE_AWARD_LIMIT = 5;

interface PaletteOption {
  readonly id: string;
  readonly label: string;
  readonly icon: IconName;
  readonly path: string;
}

/** Jumps to a section or finds an award by its title, from the keyboard. */
@Component({
  selector: 'app-command-palette',
  imports: [IconComponent, NgTemplateOutlet, TranslocoPipe],
  templateUrl: './command-palette.component.html',
  styleUrl: './command-palette.component.scss',
})
export class CommandPaletteComponent {
  private readonly dialog = inject<MatDialogRef<CommandPaletteComponent>>(MatDialogRef);
  private readonly router = inject(Router);
  private readonly transloco = inject(TranslocoService);
  private readonly awardsApi = inject(AwardsService);
  private readonly auth = inject(AuthService);
  private readonly language = inject(LanguageService);
  private readonly typed = new Subject<string>();

  protected readonly query = signal('');
  protected readonly active = signal(0);
  protected readonly awards = signal<PaletteOption[]>([]);
  protected readonly pending = signal(false);
  protected readonly searching = computed(() => this.query().trim().length >= PALETTE_MIN_QUERY);
  protected readonly sections = computed<PaletteOption[]>(() => {
    const needle = this.query().trim().toLocaleLowerCase();
    return navEntriesFor(this.auth.isAuthenticated(), this.auth.permissions())
      .map((item) => ({
        id: `section-${item.id}`,
        label: this.transloco.translate(item.label),
        icon: item.icon,
        path: item.path,
      }))
      .filter((option) => !this.searching() || option.label.toLocaleLowerCase().includes(needle));
  });
  protected readonly options = computed(() => [...this.sections(), ...this.awards()]);
  protected readonly activeId = computed(() => {
    const option = this.options()[this.active()];
    return option ? `palette-${option.id}` : null;
  });

  constructor() {
    this.typed
      .pipe(
        debounceTime(PALETTE_DEBOUNCE_MS),
        switchMap((text) => this.findAwards(text)),
        takeUntilDestroyed(inject(DestroyRef)),
      )
      .subscribe((found) => {
        this.awards.set(found);
        this.pending.set(false);
        this.clampActive();
      });
  }

  protected onInput(text: string): void {
    this.query.set(text);
    this.active.set(0);
    this.awards.set([]);
    this.pending.set(this.searching() && this.mayReadAwards());
    this.typed.next(text);
  }

  protected onKeydown(event: KeyboardEvent): void {
    const count = this.options().length;
    if (event.key === 'ArrowDown' && count > 0) {
      event.preventDefault();
      this.active.update((index) => (index + 1) % count);
    } else if (event.key === 'ArrowUp' && count > 0) {
      event.preventDefault();
      this.active.update((index) => (index - 1 + count) % count);
    } else if (event.key === 'Enter') {
      event.preventDefault();
      const option = this.options()[this.active()];
      if (option) {
        this.choose(option);
      }
    }
  }

  protected choose(option: PaletteOption): void {
    this.dialog.close();
    void this.router.navigateByUrl(option.path);
  }

  protected isActive(option: PaletteOption): boolean {
    return this.options()[this.active()]?.id === option.id;
  }

  private findAwards(text: string): Observable<PaletteOption[]> {
    const q = text.trim();
    if (q.length < PALETTE_MIN_QUERY || !this.mayReadAwards()) {
      return of([]);
    }
    return this.awardsApi.list({ ...NO_FILTERS, q }, 0, PALETTE_AWARD_LIMIT).pipe(
      map((page) => page.content.slice(0, PALETTE_AWARD_LIMIT).map((award) => this.option(award))),
      catchError(() => of([])),
    );
  }

  private mayReadAwards(): boolean {
    return canReadOwnAwards(this.auth.permissions());
  }

  private option(award: Award): PaletteOption {
    return {
      id: `award-${award.id}`,
      label: awardTitle(award, this.language.current()),
      icon: 'award',
      path: `/awards/${award.id}`,
    };
  }

  private clampActive(): void {
    const count = this.options().length;
    if (this.active() >= count) {
      this.active.set(Math.max(count - 1, 0));
    }
  }
}
