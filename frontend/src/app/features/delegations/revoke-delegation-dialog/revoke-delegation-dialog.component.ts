import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatButton } from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialogActions,
  MatDialogClose,
  MatDialogContent,
  MatDialogTitle,
} from '@angular/material/dialog';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';

import { LanguageService } from '../../../core/i18n/language.service';
import { organizationName } from '../../admin/role-organizations';
import { Delegation } from '../delegations.service';

@Component({
  selector: 'app-revoke-delegation-dialog',
  imports: [
    MatDialogTitle,
    MatDialogContent,
    MatDialogActions,
    MatDialogClose,
    MatButton,
    TranslocoPipe,
  ],
  templateUrl: './revoke-delegation-dialog.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RevokeDelegationDialogComponent {
  private readonly language = inject(LanguageService);
  private readonly transloco = inject(TranslocoService);

  protected readonly delegation = inject<Delegation>(MAT_DIALOG_DATA);

  organization(): string {
    return organizationName(this.delegation.organization, this.language.current());
  }

  roleName(): string {
    return this.transloco.translate(`roles.${this.delegation.role}`);
  }

  delegateName(): string {
    return `${this.delegation.delegate.firstName} ${this.delegation.delegate.lastName}`;
  }
}
