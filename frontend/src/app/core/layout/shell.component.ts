import { Component, inject } from '@angular/core';
import { MatButton, MatIconButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatToolbar } from '@angular/material/toolbar';
import { RouterOutlet } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

import { AuthService } from '../auth/auth.service';
import { LanguageService } from '../i18n/language.service';

@Component({
  selector: 'app-shell',
  imports: [MatToolbar, MatButton, MatIconButton, MatIcon, RouterOutlet, TranslocoPipe],
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.scss',
})
export class ShellComponent {
  protected readonly auth = inject(AuthService);
  protected readonly language = inject(LanguageService);

  logout(): void {
    void this.auth.logout();
  }
}
