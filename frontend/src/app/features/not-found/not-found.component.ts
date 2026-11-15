import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

import { StatusPageComponent } from '../../shared/status-page/status-page.component';

@Component({
  selector: 'app-not-found',
  imports: [MatButton, RouterLink, TranslocoPipe, StatusPageComponent],
  templateUrl: './not-found.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NotFoundComponent {}
