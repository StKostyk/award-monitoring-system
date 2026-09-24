import { Component, inject } from '@angular/core';
import { MatButton } from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialogActions,
  MatDialogClose,
  MatDialogContent,
  MatDialogTitle,
} from '@angular/material/dialog';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';

import { RoleAssignment } from '../../../core/auth/user-profile';
import { LanguageService } from '../../../core/i18n/language.service';
import { organizationName } from '../role-organizations';

export interface RevokeRoleData {
  assignment: RoleAssignment;
  name: string;
}

@Component({
  selector: 'app-revoke-role-dialog',
  imports: [MatDialogTitle, MatDialogContent, MatDialogActions, MatDialogClose, MatButton, TranslocoPipe],
  templateUrl: './revoke-role-dialog.component.html',
})
export class RevokeRoleDialogComponent {
  private readonly language = inject(LanguageService);
  private readonly transloco = inject(TranslocoService);

  protected readonly data = inject<RevokeRoleData>(MAT_DIALOG_DATA);

  organization(): string {
    return organizationName(this.data.assignment.organization, this.language.current());
  }

  roleName(): string {
    return this.transloco.translate(`roles.${this.data.assignment.role}`);
  }
}
