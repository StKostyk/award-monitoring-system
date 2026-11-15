import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

import { IconComponent } from '../../shared/icons/icon.component';
import { StatusPageComponent } from '../../shared/status-page/status-page.component';

@Component({
  selector: 'app-forbidden',
  imports: [MatButton, RouterLink, TranslocoPipe, StatusPageComponent, IconComponent],
  templateUrl: './forbidden.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ForbiddenComponent {}
