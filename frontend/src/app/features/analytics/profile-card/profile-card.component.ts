import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';

import { AuthService } from '../../../core/auth/auth.service';
import { RoleType } from '../../../core/auth/user-profile';
import { LanguageService } from '../../../core/i18n/language.service';
import { PaneComponent } from '../../../shared/layout/pane.component';
import { organizationName } from '../../../shared/organization-name';
import { initials } from '../../../shared/widgets/top-widget.component';

/** Roles from the one a profile names first to the last; an account without another role is an employee. */
const ROLE_PRECEDENCE: readonly RoleType[] = [
  'RECTOR',
  'DEAN',
  'RECTOR_SECRETARY',
  'FACULTY_SECRETARY',
  'GDPR_OFFICER',
  'SYSTEM_ADMIN',
  'EMPLOYEE',
];

/** The head of the personal dashboard's side column: initials, name, the leading role and the department. */
@Component({
  selector: 'app-profile-card',
  imports: [PaneComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (card(); as card) {
      <app-pane>
        <div class="profile" data-testid="profile-card">
          <span class="profile__initials" aria-hidden="true" data-testid="profile-initials">{{
            card.initials
          }}</span>
          <div class="profile__text">
            <p class="profile__name" data-testid="profile-name">{{ card.name }}</p>
            <p class="profile__meta" data-testid="profile-position">
              {{ 'roles.' + card.role | transloco }}
            </p>
            <p class="profile__meta" data-testid="profile-department">{{ card.department }}</p>
          </div>
        </div>
      </app-pane>
    }
  `,
  styles: `
    :host {
      display: block;
    }

    .profile {
      display: flex;
      align-items: center;
      gap: 16px;
      min-width: 0;
    }

    .profile__initials {
      display: inline-flex;
      flex: none;
      align-items: center;
      justify-content: center;
      width: 56px;
      height: 56px;
      border-radius: 50%;
      background: var(--mat-sys-primary-container);
      color: var(--mat-sys-on-primary-container);
      font: var(--mat-sys-title-large);
    }

    .profile__text {
      display: grid;
      gap: 2px;
      min-width: 0;
    }

    .profile__name {
      margin: 0;
      font: var(--mat-sys-title-medium);
      font-family: var(--app-font-heading);
      overflow-wrap: anywhere;
    }

    .profile__meta {
      margin: 0;
      font: var(--mat-sys-body-medium);
      color: var(--mat-sys-on-surface-variant);
      overflow-wrap: anywhere;
    }
  `,
})
export class ProfileCardComponent {
  private readonly auth = inject(AuthService);
  private readonly language = inject(LanguageService);

  protected readonly card = computed(() => {
    const profile = this.auth.profile();
    if (!profile) {
      return null;
    }
    const held = new Set(profile.roles.map((assignment) => assignment.role));
    return {
      initials: initials(this.auth.fullName()),
      name: this.auth.fullName(),
      role: ROLE_PRECEDENCE.find((role) => held.has(role)) ?? 'EMPLOYEE',
      department: organizationName(profile.organization, this.language.current()),
    };
  });
}
