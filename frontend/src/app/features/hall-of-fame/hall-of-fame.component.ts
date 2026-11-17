import { ChangeDetectionStrategy, Component, computed, effect, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatAnchor, MatButton } from '@angular/material/button';
import { MatProgressBar } from '@angular/material/progress-bar';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { Store } from '@ngrx/store';

import { AuthService } from '../../core/auth/auth.service';
import { readsUniversityHallOfFame } from '../../core/auth/permissions';
import { LanguageService } from '../../core/i18n/language.service';
import { kyivToday } from '../../shared/date-format';
import { IconComponent } from '../../shared/icons/icon.component';
import { organizationName } from '../../shared/organization-name';
import { currentKyivMonth } from '../../shared/widgets/facts';
import { TopPerson } from '../../shared/widgets/widget-data';
import { AnalyticsPeriod } from '../analytics/analytics.service';
import { UnitAnalyticsActions } from '../analytics/units/store/unit-analytics.actions';
import { unitAnalyticsFeature } from '../analytics/units/store/unit-analytics.feature';
import { UnitAnalytics } from '../analytics/units/unit-analytics.service';
import { UnitPickerComponent } from '../analytics/units/unit-picker/unit-picker.component';
import { BoardReportButtonComponent } from '../analytics/university/board-report-button/board-report-button.component';
import { UniversityAnalyticsActions } from '../analytics/university/store/university-analytics.actions';
import { universityAnalyticsFeature } from '../analytics/university/store/university-analytics.feature';
import { HallOfFameBoardComponent } from './hall-of-fame-board.component';
import { hallOfFameView, topUnits } from './hall-of-fame-view';

const ALL_YEARS: AnalyticsPeriod = { from: null, to: null };

/** «Зала визнання»: the awards of the university for a rector, of a faculty for a dean. */
@Component({
  selector: 'app-hall-of-fame',
  imports: [
    BoardReportButtonComponent,
    HallOfFameBoardComponent,
    IconComponent,
    MatAnchor,
    MatButton,
    MatProgressBar,
    RouterLink,
    TranslocoPipe,
    UnitPickerComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './hall-of-fame.component.html',
  styleUrl: './hall-of-fame.component.scss',
})
export class HallOfFameComponent {
  private readonly store = inject(Store);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly auth = inject(AuthService);
  private readonly language = inject(LanguageService);

  /** Whether the page shows the whole university. */
  readonly university = readsUniversityHallOfFame(this.auth.permissions());
  protected readonly allYears = ALL_YEARS;

  private readonly universityData = this.store.selectSignal(
    universityAnalyticsFeature.selectAnalytics,
  );
  private readonly universityLoading = this.store.selectSignal(
    universityAnalyticsFeature.selectLoading,
  );
  private readonly universityProblem = this.store.selectSignal(
    universityAnalyticsFeature.selectProblem,
  );
  private readonly units = this.store.selectSignal(unitAnalyticsFeature.selectUnits);
  private readonly unitsStatus = this.store.selectSignal(unitAnalyticsFeature.selectUnitsStatus);
  private readonly unitData = this.store.selectSignal(unitAnalyticsFeature.selectAnalytics);
  private readonly unitLoading = this.store.selectSignal(unitAnalyticsFeature.selectLoading);
  private readonly unitProblem = this.store.selectSignal(unitAnalyticsFeature.selectProblem);
  private readonly query = toSignal(this.route.queryParamMap);

  /** The faculties the caller is dean of by a role of their own, in the order the server lists them. */
  readonly faculties = computed(() => {
    const deanOf = new Set(
      this.auth
        .permissions()
        .heldScopes.filter((scope) => scope.role === 'DEAN')
        .map((scope) => scope.organizationId),
    );
    return this.units().filter((unit) => unit.type === 'FACULTY' && deanOf.has(unit.id));
  });

  /** The faculty shown: the one in `?unit=` when it is open to the caller, else the first. */
  readonly facultyId = computed(() => {
    const wanted = Number(this.query()?.get('unit'));
    const faculties = this.faculties();
    return faculties.find((unit) => unit.id === wanted)?.id ?? faculties[0]?.id ?? null;
  });

  readonly loading = computed(() =>
    this.university
      ? this.universityLoading()
      : this.unitLoading() || this.unitsStatus() === 'loading',
  );

  readonly problem = computed(() =>
    this.university
      ? this.universityProblem() !== null
      : this.unitProblem() !== null || this.unitsStatus() === 'failed',
  );

  /** Whether a dean has no faculty to show. */
  readonly noFaculty = computed(
    () => !this.university && this.unitsStatus() === 'loaded' && this.faculties().length === 0,
  );

  protected readonly unitName = computed(() => {
    const language = this.language.current();
    const unit = this.university ? this.universityData()?.unit : this.facultyData()?.unit;
    return unit ? organizationName(unit, language) : '';
  });

  private readonly facultyData = computed(() => {
    const data = this.unitData();
    return data && data.unit.id === this.facultyId() ? data : null;
  });

  readonly view = computed(() => {
    const language = this.language.current();
    const today = currentKyivMonth();
    const seed = Number(kyivToday().slice(-2));
    if (this.university) {
      const data = this.universityData();
      if (!data) {
        return null;
      }
      const faculties = topUnits(data.units, language);
      return hallOfFameView(data, faculties, faculties[0] ?? null, today, seed);
    }
    const data = this.facultyData();
    if (!data) {
      return null;
    }
    const people = facultyPeople(data);
    const leader = people[0] ?? topUnits(data.units, language)[0] ?? null;
    return hallOfFameView(data, people, leader, today, seed);
  });

  constructor() {
    if (this.university) {
      this.store.dispatch(UniversityAnalyticsActions.periodSelected({ period: ALL_YEARS }));
      return;
    }
    if (this.unitsStatus() !== 'loaded') {
      this.store.dispatch(UnitAnalyticsActions.unitsRequested());
    }
    effect(() => {
      const unitId = this.facultyId();
      if (unitId !== null) {
        this.store.dispatch(UnitAnalyticsActions.unitSelected({ unitId, period: ALL_YEARS }));
      }
    });
  }

  protected choose(unitId: number): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { unit: unitId },
      queryParamsHandling: 'merge',
    });
  }

  protected retry(): void {
    if (this.university) {
      this.store.dispatch(UniversityAnalyticsActions.periodSelected({ period: ALL_YEARS }));
      return;
    }
    const unitId = this.facultyId();
    if (unitId === null) {
      this.store.dispatch(UnitAnalyticsActions.unitsRequested());
    } else {
      this.store.dispatch(UnitAnalyticsActions.unitSelected({ unitId, period: ALL_YEARS }));
    }
  }
}

/** The top people of a faculty; none when the group is too small to list. */
function facultyPeople(data: UnitAnalytics): TopPerson[] {
  return data.topPerformers.status === 'LISTED'
    ? data.topPerformers.people.map((person) => ({
        name: `${person.firstName} ${person.lastName}`,
        count: person.count,
      }))
    : [];
}
