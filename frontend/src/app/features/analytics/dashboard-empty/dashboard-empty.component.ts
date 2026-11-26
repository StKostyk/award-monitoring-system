import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

/**
 * Guidance in place of the charts: the steps to a first award when nothing counts yet, or a way back to all
 * years when only the chosen period is empty.
 */
@Component({
  selector: 'app-dashboard-empty',
  imports: [MatButton, RouterLink, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './dashboard-empty.component.html',
})
export class DashboardEmptyComponent {
  readonly periodOnly = input(false);
  readonly canCreate = input(false);
  readonly drafts = input(0);
  readonly pending = input(0);
  readonly allYears = output();
}
