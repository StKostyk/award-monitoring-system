import { CdkDragDrop } from '@angular/cdk/drag-drop';
import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Store, provideState, provideStore } from '@ngrx/store';
import { vi } from 'vitest';

import { DashboardLayout } from '../../core/preferences/dashboard-preferences';
import { PreferencesActions } from '../../core/preferences/store/preferences.actions';
import { preferencesFeature } from '../../core/preferences/store/preferences.feature';
import { translations } from '../../features/analytics/analytics.testing';
import { DashboardLayoutComponent } from './dashboard-layout.component';
import { DashboardWidgetDirective } from './dashboard-widget.directive';

@Component({
  imports: [DashboardLayoutComponent, DashboardWidgetDirective],
  template: `
    <app-dashboard-layout dashboard="MY_AWARDS" labels="analytics.widgets.">
      <ng-template appDashboardWidget="KPI" wide><p>kpi</p></ng-template>
      <ng-template appDashboardWidget="TREND"><p>trend</p></ng-template>
      <ng-template appDashboardWidget="RANK"><p>rank</p></ng-template>
    </app-dashboard-layout>
  `,
})
class HostComponent {}

@Component({
  imports: [DashboardLayoutComponent, DashboardWidgetDirective],
  template: `
    <app-dashboard-layout dashboard="MY_AWARDS" labels="analytics.widgets." [side]="side">
      <p layoutSide data-testid="side-head">profile</p>
      <ng-template appDashboardWidget="KPI"><p>kpi</p></ng-template>
      @if (facts()) {
        <ng-template appDashboardWidget="FACT"><p>facts</p></ng-template>
      }
      <ng-template appDashboardWidget="TREND"><p>trend</p></ng-template>
      <ng-template appDashboardWidget="LATEST"><p>latest</p></ng-template>
      <ng-template appDashboardWidget="RANK"><p>rank</p></ng-template>
      <p layoutMain data-testid="main-end">timeline</p>
    </app-dashboard-layout>
  `,
})
class ColumnsHostComponent {
  readonly side = ['KPI', 'FACT'];
  readonly facts = signal(true);
}

describe('DashboardLayoutComponent in two columns', () => {
  let fixture: ComponentFixture<ColumnsHostComponent>;
  let store: Store;
  let dispatch: ReturnType<typeof vi.spyOn>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ColumnsHostComponent, translations('en')],
      providers: [provideStore({}), provideState(preferencesFeature)],
    }).compileComponents();
    store = TestBed.inject(Store);
    dispatch = vi.spyOn(store, 'dispatch');
    fixture = TestBed.createComponent(ColumnsHostComponent);
    document.body.appendChild(fixture.nativeElement);
  });

  afterEach(() => fixture.nativeElement.remove());

  function load(layout?: DashboardLayout): void {
    store.dispatch(
      PreferencesActions.loaded({
        preferences: { colorScheme: 'SYSTEM', layouts: layout ? { MY_AWARDS: layout } : {} },
      }),
    );
    fixture.detectChanges();
  }

  function element(testId: string): HTMLElement {
    return fixture.nativeElement.querySelector(`[data-testid="${testId}"]`);
  }

  function ids(scope: string, prefix: string): string[] {
    return [...fixture.nativeElement.querySelectorAll(`${scope} [data-testid^="${prefix}"]`)].map(
      (node) => (node as HTMLElement).dataset['testid']!.replace(prefix, ''),
    );
  }

  function edit(): void {
    element('dashboard-customise').click();
    fixture.detectChanges();
  }

  it('ac2_1_puts_each_widget_in_its_column_with_the_projected_head_and_end', () => {
    load();

    expect(ids('.split__side', 'widget-')).toEqual(['KPI', 'FACT']);
    expect(ids('.split__main', 'widget-')).toEqual(['TREND', 'LATEST', 'RANK']);
    expect(element('side-head').closest('.split__side')).not.toBeNull();
    expect(element('main-end').closest('.split__main')).not.toBeNull();
  });

  it('ac2_4_a_stored_9_4_order_shows_the_new_widgets_after_the_ordered_ones_of_their_column', () => {
    load({ order: ['RANK', 'KPI', 'TREND'], hidden: [] });

    expect(ids('.split__side', 'widget-')).toEqual(['KPI', 'FACT']);
    expect(ids('.split__main', 'widget-')).toEqual(['RANK', 'TREND', 'LATEST']);
  });

  it('ac2_3_moves_only_within_the_column_and_announces_the_column_position', async () => {
    load();
    edit();

    expect(ids('.split__side', 'layout-item-')).toEqual(['KPI', 'FACT']);
    expect(ids('.split__main', 'layout-item-')).toEqual(['TREND', 'LATEST', 'RANK']);
    expect((element('layout-down-FACT') as HTMLButtonElement).disabled).toBe(true);
    expect((element('layout-up-TREND') as HTMLButtonElement).disabled).toBe(true);

    element('layout-up-FACT').click();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(ids('.split__side', 'layout-item-')).toEqual(['FACT', 'KPI']);
    expect(element('layout-announcement').textContent).toContain('position 1 of 2');
  });

  it('ac2_3_stores_one_order_and_one_hidden_list_and_keeps_the_other_column_in_place', () => {
    load();
    edit();
    element('layout-up-FACT').click();
    fixture.detectChanges();
    (
      fixture.debugElement.children[0].componentInstance as {
        drop(event: { previousIndex: number; currentIndex: number }, column: string): void;
      }
    ).drop({ previousIndex: 2, currentIndex: 0 }, 'main');
    element('layout-hide-LATEST').click();
    fixture.detectChanges();
    element('layout-done').click();
    fixture.detectChanges();

    expect(dispatch).toHaveBeenCalledWith(
      PreferencesActions.layoutChanged({
        dashboard: 'MY_AWARDS',
        layout: { order: ['FACT', 'KPI', 'RANK', 'TREND', 'LATEST'], hidden: ['LATEST'] },
      }),
    );
    expect(ids('.split__main', 'widget-')).toEqual(['RANK', 'TREND']);
  });
  it('ac2_3_keeps_the_place_of_a_widget_the_period_leaves_out_when_saving', () => {
    fixture.componentInstance.facts.set(false);
    load({ order: ['FACT', 'KPI', 'TREND', 'LATEST', 'RANK'], hidden: [] });
    edit();
    element('layout-hide-RANK').click();
    fixture.detectChanges();
    element('layout-done').click();
    fixture.detectChanges();

    expect(dispatch).toHaveBeenCalledWith(
      PreferencesActions.layoutChanged({
        dashboard: 'MY_AWARDS',
        layout: { order: ['FACT', 'KPI', 'TREND', 'LATEST', 'RANK'], hidden: ['RANK'] },
      }),
    );
  });

  it('ac2_1_tells_how_to_bring_back_the_main_column_when_all_its_widgets_are_hidden', () => {
    load({ order: [], hidden: ['TREND', 'LATEST', 'RANK'] });

    expect(ids('.split__side', 'widget-')).toEqual(['KPI', 'FACT']);
    expect(element('dashboard-all-hidden')?.closest('.split__main')).not.toBeNull();
  });
});

describe('DashboardLayoutComponent', () => {
  let fixture: ComponentFixture<HostComponent>;
  let store: Store;
  let dispatch: ReturnType<typeof vi.spyOn>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [HostComponent, translations('en')],
      providers: [provideStore({}), provideState(preferencesFeature)],
    }).compileComponents();
    store = TestBed.inject(Store);
    dispatch = vi.spyOn(store, 'dispatch');
    fixture = TestBed.createComponent(HostComponent);
    document.body.appendChild(fixture.nativeElement);
  });

  afterEach(() => fixture.nativeElement.remove());

  function load(layout?: DashboardLayout): void {
    store.dispatch(
      PreferencesActions.loaded({
        preferences: { colorScheme: 'SYSTEM', layouts: layout ? { MY_AWARDS: layout } : {} },
      }),
    );
    fixture.detectChanges();
  }

  function element(testId: string): HTMLElement {
    return fixture.nativeElement.querySelector(`[data-testid="${testId}"]`);
  }

  function ids(prefix: string): string[] {
    return [...fixture.nativeElement.querySelectorAll(`[data-testid^="${prefix}"]`)].map((node) =>
      (node as HTMLElement).dataset['testid']!.replace(prefix, ''),
    );
  }

  function edit(): void {
    element('dashboard-customise').click();
    fixture.detectChanges();
  }

  function layoutComponent(): { drop(event: Partial<CdkDragDrop<string[]>>): void } {
    return fixture.debugElement.children[0].componentInstance;
  }

  it('ac1_3_shows_the_provided_widgets_in_the_stored_order_without_hidden_ones', () => {
    load({ order: ['RANK'], hidden: ['TREND'] });

    expect(ids('widget-')).toEqual(['RANK', 'KPI']);
    expect(element('widget-KPI').classList).toContain('layout__cell--wide');
  });

  it('ac1_8_moves_by_keyboard_keeps_the_focus_and_announces_the_position', async () => {
    load();
    edit();

    expect(ids('layout-item-')).toEqual(['KPI', 'TREND', 'RANK']);
    expect((element('layout-up-KPI') as HTMLButtonElement).disabled).toBe(true);

    element('layout-down-KPI').focus();
    element('layout-down-KPI').click();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(ids('layout-item-')).toEqual(['TREND', 'KPI', 'RANK']);
    expect(document.activeElement).toBe(element('layout-down-KPI'));
    expect(element('layout-announcement').textContent).toContain('“Summary”: position 2 of 3');

    element('layout-down-KPI').click();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(ids('layout-item-')).toEqual(['TREND', 'RANK', 'KPI']);
    expect(document.activeElement).toBe(element('layout-up-KPI'));
  });

  it('ac1_3_saves_moves_by_drag_and_hidden_widgets_on_done', () => {
    load({ order: [], hidden: ['VISIBILITY'] });
    edit();
    layoutComponent().drop({ previousIndex: 2, currentIndex: 0 });
    element('layout-hide-TREND').click();
    fixture.detectChanges();

    expect(element('layout-hide-TREND').getAttribute('aria-pressed')).toBe('true');
    expect(element('layout-announcement').textContent).toContain('“Awards by year” hidden');

    element('layout-done').click();
    fixture.detectChanges();

    expect(dispatch).toHaveBeenCalledWith(
      PreferencesActions.layoutChanged({
        dashboard: 'MY_AWARDS',
        layout: { order: ['RANK', 'KPI', 'TREND'], hidden: ['TREND', 'VISIBILITY'] },
      }),
    );
    expect(ids('widget-')).toEqual(['RANK', 'KPI']);
  });

  it('ac1_3_resets_to_the_default_and_clears_the_stored_layout', () => {
    load({ order: ['RANK'], hidden: ['KPI'] });
    edit();
    element('layout-reset').click();
    fixture.detectChanges();

    expect(ids('layout-item-')).toEqual(['KPI', 'TREND', 'RANK']);

    element('layout-done').click();
    fixture.detectChanges();

    expect(dispatch).toHaveBeenCalledWith(
      PreferencesActions.layoutChanged({ dashboard: 'MY_AWARDS', layout: null }),
    );
    expect(ids('widget-')).toEqual(['KPI', 'TREND', 'RANK']);
  });

  it('ac1_3_saves_nothing_when_the_layout_did_not_change', () => {
    load();
    edit();
    dispatch.mockClear();
    element('layout-done').click();

    expect(dispatch).not.toHaveBeenCalled();
  });

  it('ac1_3_brings_a_hidden_widget_back', () => {
    load({ order: [], hidden: ['KPI', 'TREND', 'RANK'] });

    expect(element('dashboard-all-hidden').textContent).toContain('All widgets are hidden');

    edit();
    element('layout-hide-KPI').click();
    fixture.detectChanges();

    expect(element('layout-announcement').textContent).toContain('“Summary” shown');
  });
});
