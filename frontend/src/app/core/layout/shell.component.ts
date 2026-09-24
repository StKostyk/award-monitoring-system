import { Component, computed, inject } from '@angular/core';
import { MatButton, MatIconButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatToolbar } from '@angular/material/toolbar';
import { RouterLink, RouterOutlet } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

import { AuthService } from '../auth/auth.service';
import { canReadDirectory } from '../auth/permissions';
import { LanguageService } from '../i18n/language.service';

@Component({
  selector: 'app-shell',
  imports: [MatToolbar, MatButton, MatIconButton, MatIcon, RouterLink, RouterOutlet, TranslocoPipe],
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.scss',
})
export class ShellComponent {
  protected readonly auth = inject(AuthService);
  protected readonly language = inject(LanguageService);
  protected readonly canOpenUsers = computed(
    () => this.auth.isAuthenticated() && canReadDirectory(this.auth.permissions()),
  );

  logout(): void {
    void this.auth.logout();
  }
}
