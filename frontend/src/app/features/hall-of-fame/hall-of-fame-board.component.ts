import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';

import { DashboardLayoutComponent } from '../../shared/dashboard/dashboard-layout.component';
import { DashboardWidgetDirective } from '../../shared/dashboard/dashboard-widget.directive';
import { PaneComponent } from '../../shared/layout/pane.component';
import { FactWidgetComponent } from '../../shared/widgets/fact-widget.component';
import { LevelMapWidgetComponent } from '../../shared/widgets/level-map-widget.component';
import { RaceWidgetComponent } from '../../shared/widgets/race-widget.component';
import { RecordsWidgetComponent } from '../../shared/widgets/records-widget.component';
import { TopWidgetComponent } from '../../shared/widgets/top-widget.component';
import { TrendWidgetComponent } from '../../shared/widgets/trend-widget.component';
import { HallOfFameView } from './hall-of-fame-view';
import { HallOfFameHeroComponent } from './hero/hall-of-fame-hero.component';

/** The widgets of the Hall of Fame in the order and visibility the account chose. */
@Component({
  selector: 'app-hall-of-fame-board',
  imports: [
    DashboardLayoutComponent,
    DashboardWidgetDirective,
    FactWidgetComponent,
    HallOfFameHeroComponent,
    LevelMapWidgetComponent,
    PaneComponent,
    RaceWidgetComponent,
    RecordsWidgetComponent,
    TopWidgetComponent,
    TranslocoPipe,
    TrendWidgetComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './hall-of-fame-board.component.html',
  styleUrl: './hall-of-fame-board.component.scss',
})
export class HallOfFameBoardComponent {
  /** The data of every widget. */
  readonly view = input.required<HallOfFameView>();
  /** Whether the board shows the university, so P2 lists faculties rather than people. */
  readonly university = input(false);
}
