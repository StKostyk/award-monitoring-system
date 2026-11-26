import { DatePipe, formatDate, NgComponentOutlet } from '@angular/common';
import { Component, computed, inject, LOCALE_ID, signal, Type } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButton } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatError, MatFormField, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { combineLatest, map } from 'rxjs';

import { fieldProblems, problemType } from '../../../core/api/problem';
import { AuthService } from '../../../core/auth/auth.service';
import { OrganizationRef, UserProfile } from '../../../core/auth/user-profile';
import { LanguageService } from '../../../core/i18n/language.service';
import { IconComponent } from '../../../shared/icons/icon.component';
import { IconName } from '../../../shared/icons/icons';
import { ListRowComponent, ListRowsDirective } from '../../../shared/layout/list-row.component';
import { PageHeaderComponent } from '../../../shared/layout/page-header.component';
import { PaneComponent } from '../../../shared/layout/pane.component';
import { SplitComponent } from '../../../shared/layout/split.component';
import { nameValidator } from '../../../shared/name-rules';
import { organizationName } from '../../../shared/organization-name';
import { Delegation, DelegationsService } from '../../delegations/delegations.service';
import { EmailChangeDialogComponent } from '../email-change-dialog/email-change-dialog.component';
import { PREFERENCE_COUNT } from '../notification-preferences/notification-preferences.component';
import { NameChange, ProfileService } from '../profile.service';

type NameField = 'firstName' | 'lastName';

/** A section of the profile page, kept in the `section` query parameter. */
export type ProfileSection = 'names' | 'address' | 'notifications' | 'privacy' | 'roles';

export const PROFILE_SECTIONS: readonly { id: ProfileSection; icon: IconName }[] = [
  { id: 'names', icon: 'user' },
  { id: 'address', icon: 'mail' },
  { id: 'notifications', icon: 'bell' },
  { id: 'privacy', icon: 'shield' },
  { id: 'roles', icon: 'list' },
];

/**
 * Reads a profile section from the query parameter, or from the old `#notifications` anchor.
 *
 * @param value the `section` query parameter
 * @param fragment the URL fragment
 * @returns the section, or null when none or an unknown one is named
 */
export function sectionOf(value: string | null, fragment: string | null): ProfileSection | null {
  const named = value ?? (fragment === 'notifications' ? fragment : null);
  return PROFILE_SECTIONS.find((section) => section.id === named)?.id ?? null;
}

const DATE = 'dd.MM.yyyy';

@Component({
  selector: 'app-profile',
  imports: [
    ReactiveFormsModule,
    DatePipe,
    NgComponentOutlet,
    MatFormField,
    MatLabel,
    MatError,
    MatInput,
    MatButton,
    RouterLink,
    TranslocoPipe,
    IconComponent,
    ListRowComponent,
    ListRowsDirective,
    PageHeaderComponent,
    PaneComponent,
    SplitComponent,
  ],
  templateUrl: './profile.component.html',
  styleUrl: './profile.component.scss',
})
export class ProfileComponent {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly api = inject(ProfileService);
  private readonly delegations = inject(DelegationsService);
  private readonly language = inject(LanguageService);
  private readonly transloco = inject(TranslocoService);
  private readonly dialog = inject(MatDialog);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly locale = inject(LOCALE_ID);

  readonly sections = PROFILE_SECTIONS;
  readonly privacyLinks = ['consents', 'export', 'erasure'] as const;
  readonly profile = signal<UserProfile | null>(null);
  readonly received = signal<Delegation[]>([]);
  readonly loadFailed = signal(false);
  readonly saving = signal(false);
  readonly message = signal<string | null>(null);
  readonly problem = signal<string | null>(null);
  readonly sentTo = signal<string | null>(null);
  readonly preferencesCard = signal<Type<unknown> | null>(null);

  /** The section named in the URL, null when none or an unknown one is. */
  readonly chosen = toSignal(
    combineLatest([this.route.queryParamMap, this.route.fragment]).pipe(
      map(([params, fragment]) => sectionOf(params.get('section'), fragment)),
    ),
    {
      initialValue: sectionOf(
        this.route.snapshot.queryParamMap.get('section'),
        this.route.snapshot.fragment,
      ),
    },
  );
  /** The section on the right: the chosen one, or the first at 960 px and wider. */
  readonly shown = computed<ProfileSection>(() => this.chosen() ?? PROFILE_SECTIONS[0].id);
  readonly initials = computed(() => {
    const profile = this.profile();
    return profile ? `${profile.firstName.charAt(0)}${profile.lastName.charAt(0)}` : '';
  });

  readonly names = this.fb.nonNullable.group({
    firstName: ['', nameValidator],
    lastName: ['', nameValidator],
  });

  constructor() {
    this.auth
      .loadProfile()
      .then((profile) => this.show(profile))
      .catch(() => this.loadFailed.set(true));
    this.delegations.list('active').subscribe({
      next: (list) => this.received.set(list.received),
      error: () => this.received.set([]),
    });
    import('../notification-preferences/notification-preferences.component')
      .then((module) => this.preferencesCard.set(module.NotificationPreferencesComponent))
      .catch(() => this.preferencesCard.set(null));
  }

  name(organization: OrganizationRef): string {
    return organizationName(organization, this.language.current());
  }

  /** The first role and its unit, or the person's unit when they hold no role. */
  roleLine(profile: UserProfile): string {
    const role = profile.roles[0];
    return role
      ? `${this.transloco.translate('roles.' + role.role)} · ${this.name(role.organization)}`
      : this.name(profile.organization);
  }

  /**
   * The one-line summary under a section in the list.
   *
   * @param section the section
   * @param profile the signed-in person
   * @returns the summary
   */
  summary(section: ProfileSection, profile: UserProfile): string {
    switch (section) {
      case 'names':
        return `${profile.firstName} ${profile.lastName}`;
      case 'address':
        return profile.email;
      case 'notifications':
        return this.transloco.translate('profile.sections.notificationsSummary', {
          count: PREFERENCE_COUNT,
        });
      case 'privacy':
        return this.transloco.translate('profile.sections.privacySummary');
      case 'roles':
        return this.rolesSummary(profile);
    }
  }

  select(section: ProfileSection | null): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { section },
      queryParamsHandling: 'merge',
    });
  }

  /** The code of the first error of a name field, as the API would report it. */
  firstError(field: NameField): string | null {
    const errors = this.names.controls[field].errors;
    return errors ? Object.keys(errors)[0] : null;
  }

  save(): void {
    const current = this.profile();
    if (!current || this.names.invalid || this.names.pristine || this.saving()) {
      return;
    }
    const change: NameChange = {};
    const { firstName, lastName } = this.names.getRawValue();
    if (firstName.trim() !== current.firstName) {
      change.firstName = firstName.trim();
    }
    if (lastName.trim() !== current.lastName) {
      change.lastName = lastName.trim();
    }
    this.saving.set(true);
    this.message.set(null);
    this.problem.set(null);
    this.api.updateNames(change).subscribe({
      next: (profile) => {
        this.auth.profile.set(profile);
        this.show(profile);
        this.message.set('profile.saved');
        this.saving.set(false);
      },
      error: (err: unknown) => {
        this.showProblem(err);
        this.saving.set(false);
      },
    });
  }

  changeAddress(): void {
    const current = this.profile();
    if (!current) {
      return;
    }
    this.dialog
      .open<EmailChangeDialogComponent, { email: string }, string>(EmailChangeDialogComponent, {
        data: { email: current.email },
        width: '480px',
      })
      .afterClosed()
      .subscribe((newEmail) => {
        if (newEmail) {
          this.sentTo.set(newEmail);
        }
      });
  }

  private rolesSummary(profile: UserProfile): string {
    const role = profile.roles[0];
    if (!role) {
      return this.transloco.translate('profile.noRoles');
    }
    const first = `${this.transloco.translate('roles.' + role.role)} · ${this.transloco.translate(
      'profile.validity',
      { from: formatDate(role.validFrom, DATE, this.locale) },
    )}`;
    return profile.roles.length > 1 ? `${first} · +${profile.roles.length - 1}` : first;
  }

  private show(profile: UserProfile): void {
    this.profile.set(profile);
    this.names.reset({ firstName: profile.firstName, lastName: profile.lastName });
  }

  private showProblem(err: unknown): void {
    const fields = fieldProblems(err);
    fields
      .filter((field) => field.field === 'firstName' || field.field === 'lastName')
      .forEach((field) =>
        this.names.controls[field.field as NameField].setErrors({ [field.code]: true }),
      );
    if (fields.length === 0) {
      const type = problemType(err);
      this.problem.set(type === 'network' ? 'profile.errors.network' : 'profile.errors.failed');
    }
  }
}
