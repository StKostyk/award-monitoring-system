import { Component, inject } from '@angular/core';
import { MatCard, MatCardContent, MatCardHeader, MatCardTitle } from '@angular/material/card';
import { MatChip, MatChipSet } from '@angular/material/chips';
import { TranslocoPipe } from '@jsverse/transloco';

import { AuthService } from '../../core/auth/auth.service';
import { LanguageService } from '../../core/i18n/language.service';
import { OrganizationRef } from '../../core/auth/user-profile';

@Component({
  selector: 'app-home',
  imports: [MatCard, MatCardHeader, MatCardTitle, MatCardContent, MatChipSet, MatChip, TranslocoPipe],
  templateUrl: './home.component.html',
  styleUrl: './home.component.scss',
})
export class HomeComponent {
  protected readonly auth = inject(AuthService);
  private readonly language = inject(LanguageService);

  organizationName(organization: OrganizationRef): string {
    return this.language.current() === 'uk' && organization.nameUk ? organization.nameUk : organization.name;
  }
}
