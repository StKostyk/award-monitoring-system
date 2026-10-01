import { Component, computed, inject } from '@angular/core';
import { MatCard, MatCardContent, MatCardHeader, MatCardTitle } from '@angular/material/card';
import { MatChip, MatChipSet } from '@angular/material/chips';
import { TranslocoPipe } from '@jsverse/transloco';

import { AuthService } from '../../core/auth/auth.service';
import { canCreateAwards } from '../../core/auth/permissions';
import { LanguageService } from '../../core/i18n/language.service';
import { OrganizationRef } from '../../core/auth/user-profile';
import { organizationName } from '../../shared/organization-name';
import { MySubmissionsComponent } from './my-submissions.component';

@Component({
  selector: 'app-home',
  imports: [
    MatCard,
    MatCardHeader,
    MatCardTitle,
    MatCardContent,
    MatChipSet,
    MatChip,
    MySubmissionsComponent,
    TranslocoPipe,
  ],
  templateUrl: './home.component.html',
  styleUrl: './home.component.scss',
})
export class HomeComponent {
  protected readonly auth = inject(AuthService);
  private readonly language = inject(LanguageService);
  protected readonly canSubmit = computed(() => canCreateAwards(this.auth.permissions()));

  organizationName(organization: OrganizationRef): string {
    return organizationName(organization, this.language.current());
  }
}
