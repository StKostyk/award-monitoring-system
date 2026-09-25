import { Component, computed, effect, inject, signal } from '@angular/core';
import { MatButton, MatIconButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatToolbar } from '@angular/material/toolbar';
import { RouterLink, RouterOutlet } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

import { DelegationsService } from '../../features/delegations/delegations.service';
import { AuthService } from '../auth/auth.service';
import { canDelegate, canReadDirectory } from '../auth/permissions';
import { LanguageService } from '../i18n/language.service';

@Component({
  selector: 'app-shell',
  imports: [MatToolbar, MatButton, MatIconButton, MatIcon, RouterLink, RouterOutlet, TranslocoPipe],
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.scss',
})
export class ShellComponent {
  private readonly delegations = inject(DelegationsService);

  protected readonly auth = inject(AuthService);
  protected readonly language = inject(LanguageService);
  protected readonly canOpenUsers = computed(
    () => this.auth.isAuthenticated() && canReadDirectory(this.auth.permissions()),
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

  private readActingFor(): void {
    if (!this.auth.isAuthenticated() || this.auth.permissions().delegations.length === 0) {
      this.actingFor.set('');
      return;
    }
    this.delegations.list('active').subscribe({
      next: (list) =>
        this.actingFor.set(
          list.received
            .map((delegation) => `${delegation.delegator.firstName} ${delegation.delegator.lastName}`)
            .join(', '),
        ),
      error: () => this.actingFor.set(''),
    });
  }
}
