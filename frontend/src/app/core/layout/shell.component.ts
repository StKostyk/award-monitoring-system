import { BreakpointObserver } from '@angular/cdk/layout';
import { Component, computed, effect, inject, signal, viewChild } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButton, MatIconButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatListItem, MatListItemIcon, MatListItemTitle, MatNavList } from '@angular/material/list';
import { MatMenu, MatMenuItem, MatMenuTrigger } from '@angular/material/menu';
import { MatSidenav, MatSidenavContainer, MatSidenavContent } from '@angular/material/sidenav';
import { MatToolbar } from '@angular/material/toolbar';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { map } from 'rxjs';

import { DelegationsService } from '../../features/delegations/delegations.service';
import { AuthService } from '../auth/auth.service';
import { canDelegate, canReadDirectory, canReadOwnAwards, canReview } from '../auth/permissions';
import { BrandService } from '../brand/brand.service';
import { COLOR_SCHEMES, ColorScheme, ColorSchemeService } from '../brand/color-scheme.service';
import { LanguageService } from '../i18n/language.service';

export const WIDE_LAYOUT = '(min-width: 960px)';

@Component({
  selector: 'app-shell',
  imports: [
    MatToolbar,
    MatButton,
    MatIconButton,
    MatIcon,
    MatListItem,
    MatListItemIcon,
    MatListItemTitle,
    MatMenu,
    MatMenuItem,
    MatMenuTrigger,
    MatNavList,
    MatSidenav,
    MatSidenavContainer,
    MatSidenavContent,
    RouterLink,
    RouterLinkActive,
    RouterOutlet,
    TranslocoPipe,
  ],
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.scss',
})
export class ShellComponent {
  private readonly delegations = inject(DelegationsService);
  private readonly router = inject(Router);
  private readonly drawer = viewChild<MatSidenav>('drawer');

  protected readonly auth = inject(AuthService);
  protected readonly language = inject(LanguageService);
  protected readonly brands = inject(BrandService);
  protected readonly colorScheme = inject(ColorSchemeService);
  protected readonly brand = this.brands.brand;
  protected readonly schemes = COLOR_SCHEMES;
  protected readonly schemeIcons: Record<ColorScheme, string> = {
    system: 'brightness_auto',
    light: 'light_mode',
    dark: 'dark_mode',
  };
  protected readonly wide = toSignal(
    inject(BreakpointObserver)
      .observe(WIDE_LAYOUT)
      .pipe(map((state) => state.matches)),
    { initialValue: true },
  );
  protected readonly canOpenAwards = computed(
    () => this.auth.isAuthenticated() && canReadOwnAwards(this.auth.permissions()),
  );
  protected readonly canOpenUsers = computed(
    () => this.auth.isAuthenticated() && canReadDirectory(this.auth.permissions()),
  );
  protected readonly canOpenReviews = computed(
    () => this.auth.isAuthenticated() && canReview(this.auth.permissions()),
  );
  protected readonly canOpenDelegations = computed(
    () => this.auth.isAuthenticated() && canDelegate(this.auth.permissions()),
  );
  protected readonly actingFor = signal('');

  constructor() {
    effect(() => this.readActingFor());
  }

  logout(): void {
    void this.auth.logout();
  }

  /** Signs in and comes back to the page the visitor is on. */
  login(): void {
    this.auth.login(this.router.url);
  }

  protected closeDrawer(): void {
    if (!this.wide()) {
      void this.drawer()?.close();
    }
  }

  private readActingFor(): void {
    if (!this.auth.isAuthenticated() || this.auth.permissions().delegations.length === 0) {
      this.actingFor.set('');
      return;
    }
    this.delegations.list('active').subscribe({
      next: (list) =>
        this.actingFor.set(
          list.received
            .map(
              (delegation) => `${delegation.delegator.firstName} ${delegation.delegator.lastName}`,
            )
            .join(', '),
        ),
      error: () => this.actingFor.set(''),
    });
  }
}
