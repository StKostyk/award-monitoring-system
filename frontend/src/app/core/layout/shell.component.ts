import { BreakpointObserver } from '@angular/cdk/layout';
import {
  Component,
  DestroyRef,
  ElementRef,
  computed,
  effect,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButton, MatIconButton } from '@angular/material/button';
import { MatListItem, MatListItemIcon, MatListItemTitle, MatNavList } from '@angular/material/list';
import { MatMenu, MatMenuItem, MatMenuTrigger } from '@angular/material/menu';
import { MatSidenav, MatSidenavContainer, MatSidenavContent } from '@angular/material/sidenav';
import { MatToolbar } from '@angular/material/toolbar';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { filter, map } from 'rxjs';

import { DelegationsService } from '../../features/delegations/delegations.service';
import { PrivacyCentreService } from '../../features/privacy/privacy-centre.service';
import { kyivDate } from '../../shared/date-format';
import { IconComponent } from '../../shared/icons/icon.component';
import { IconName } from '../../shared/icons/icons';
import { NARROW_SCREEN } from '../../shared/touch';
import { AuthService } from '../auth/auth.service';
import { canCreateAwards } from '../auth/permissions';
import { BrandService } from '../brand/brand.service';
import { COLOR_SCHEMES, ColorScheme, ColorSchemeService } from '../brand/color-scheme.service';
import { OfflineNoticeComponent } from '../connectivity/offline-notice.component';
import { LanguageService } from '../i18n/language.service';
import { PageTitleStrategy } from '../i18n/page-title.strategy';
import { NotificationBellComponent } from '../notifications/notification-bell/notification-bell.component';
import { APPLE, CommandPaletteService } from './command-palette.service';
import { NavigationFocusService } from './navigation-focus.service';
import { NavEntry, navEntriesFor } from './nav-entries';

export const WIDE_LAYOUT = '(min-width: 960px)';

@Component({
  selector: 'app-shell',
  imports: [
    MatToolbar,
    MatButton,
    MatIconButton,
    IconComponent,
    MatListItem,
    MatListItemIcon,
    MatListItemTitle,
    MatMenu,
    MatMenuItem,
    MatMenuTrigger,
    MatNavList,
    NotificationBellComponent,
    OfflineNoticeComponent,
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
  private readonly privacy = inject(PrivacyCentreService);
  private readonly router = inject(Router);
  private readonly drawer = viewChild<MatSidenav>('drawer');
  private readonly main = viewChild<ElementRef<HTMLElement>>('main');

  protected readonly auth = inject(AuthService);
  protected readonly language = inject(LanguageService);
  protected readonly brands = inject(BrandService);
  protected readonly colorScheme = inject(ColorSchemeService);
  protected readonly palette = inject(CommandPaletteService);
  protected readonly brand = this.brands.brand;
  protected readonly schemes = COLOR_SCHEMES;
  protected readonly schemeIcons: Record<ColorScheme, IconName> = {
    system: 'monitor',
    light: 'sun',
    dark: 'moon',
  };
  protected readonly wide = toSignal(
    inject(BreakpointObserver)
      .observe(WIDE_LAYOUT)
      .pipe(map((state) => state.matches)),
    { initialValue: true },
  );
  protected readonly compact = toSignal(
    inject(BreakpointObserver)
      .observe(NARROW_SCREEN)
      .pipe(map((state) => state.breakpoints[NARROW_SCREEN] === true)),
    { initialValue: false },
  );
  protected readonly entries = computed(() =>
    navEntriesFor(this.auth.isAuthenticated(), this.auth.permissions()),
  );
  protected readonly canCreate = computed(
    () => this.auth.isAuthenticated() && canCreateAwards(this.auth.permissions()),
  );
  protected readonly pageTitle = inject(PageTitleStrategy).page;
  protected readonly shortcut = APPLE.test(globalThis.navigator?.userAgent ?? '') ? '⌘K' : 'Ctrl K';
  private readonly url = toSignal(
    this.router.events.pipe(
      filter((event) => event instanceof NavigationEnd),
      map((event) => event.urlAfterRedirects),
    ),
    { initialValue: this.router.url },
  );
  protected readonly actingFor = signal('');
  protected readonly erasureDate = computed(() => {
    const pending = this.privacy.erasure();
    return this.auth.isAuthenticated() && pending
      ? kyivDate(pending.scheduledFor, this.language.current())
      : null;
  });

  constructor() {
    effect(() => this.readActingFor());
    effect(() => this.readErasure());
    const destroyRef = inject(DestroyRef);
    inject(NavigationFocusService).follow(() => this.main()?.nativeElement, destroyRef);
    this.palette.listen(destroyRef);
  }

  /**
   * Whether an active router link should mark its entry current, unless the URL belongs to a child entry.
   *
   * @param item the side menu entry
   * @param active whether its router link is active
   * @return whether the entry is the current page
   */
  protected current(item: NavEntry, active: boolean): boolean {
    return active && !(item.excludes && this.url().startsWith(item.excludes));
  }

  /** Moves the focus to the page content, past the navigation. */
  protected skipToContent(event: Event): void {
    event.preventDefault();
    this.main()?.nativeElement.focus();
  }

  protected openPalette(): void {
    void this.palette.open();
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

  private readErasure(): void {
    if (!this.auth.isAuthenticated()) {
      this.privacy.forgetErasure();
      return;
    }
    this.privacy.summary().subscribe({ error: () => this.privacy.forgetErasure() });
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
