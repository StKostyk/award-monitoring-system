import {
  CdkDrag,
  CdkDragDrop,
  CdkDragHandle,
  CdkDropList,
  moveItemInArray,
} from '@angular/cdk/drag-drop';
import { NgTemplateOutlet } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Injector,
  afterNextRender,
  computed,
  contentChildren,
  inject,
  input,
  signal,
} from '@angular/core';
import { MatButton, MatIconButton } from '@angular/material/button';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { Store } from '@ngrx/store';

import {
  DASHBOARD_WIDGETS,
  DashboardId,
  DashboardLayout,
  arrange,
} from '../../core/preferences/dashboard-preferences';
import { PreferencesActions } from '../../core/preferences/store/preferences.actions';
import { preferencesFeature } from '../../core/preferences/store/preferences.feature';
import { IconComponent } from '../icons/icon.component';
import { DashboardWidgetDirective } from './dashboard-widget.directive';

/**
 * Shows the widgets of a dashboard in the order the account keeps and lets it reorder and hide them in edit mode, by
 * dragging or with the move and hide buttons.
 */
@Component({
  selector: 'app-dashboard-layout',
  imports: [
    CdkDrag,
    CdkDragHandle,
    CdkDropList,
    IconComponent,
    MatButton,
    MatIconButton,
    NgTemplateOutlet,
    TranslocoPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './dashboard-layout.component.html',
  styleUrl: './dashboard-layout.component.scss',
})
export class DashboardLayoutComponent {
  private readonly store = inject(Store);
  private readonly transloco = inject(TranslocoService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);

  readonly dashboard = input.required<DashboardId>();
  readonly labels = input.required<string>();

  protected readonly status = this.store.selectSignal(preferencesFeature.selectStatus);
  protected readonly saveProblem = this.store.selectSignal(preferencesFeature.selectSaveProblem);
  private readonly layouts = this.store.selectSignal(preferencesFeature.selectLayouts);
  private readonly widgets = contentChildren(DashboardWidgetDirective);

  protected readonly editing = signal(false);
  protected readonly draftOrder = signal<string[]>([]);
  protected readonly draftHidden = signal(new Set<string>());
  protected readonly announcement = signal('');

  private readonly available = computed(() => {
    const provided = new Set(this.widgets().map((widget) => widget.id()));
    return DASHBOARD_WIDGETS[this.dashboard()].filter((id) => provided.has(id));
  });
  private readonly stored = computed(() => this.layouts()[this.dashboard()]);
  protected readonly shown = computed(() => {
    const hidden = new Set(this.stored()?.hidden ?? []);
    const byId = new Map(this.widgets().map((widget) => [widget.id(), widget]));
    return arrange(this.available(), this.stored())
      .filter((id) => !hidden.has(id))
      .map((id) => byId.get(id) as DashboardWidgetDirective);
  });

  protected edit(): void {
    this.draftOrder.set(arrange(this.available(), this.stored()));
    this.draftHidden.set(
      new Set((this.stored()?.hidden ?? []).filter((id) => this.isAvailable(id))),
    );
    this.announcement.set('');
    this.editing.set(true);
  }

  protected move(id: string, step: -1 | 1): void {
    const order = [...this.draftOrder()];
    const from = order.indexOf(id);
    moveItemInArray(order, from, from + step);
    this.draftOrder.set(order);
    this.announceMove(id);
    this.keepFocus(id, step);
  }

  protected drop(event: CdkDragDrop<string[]>): void {
    const order = [...this.draftOrder()];
    moveItemInArray(order, event.previousIndex, event.currentIndex);
    this.draftOrder.set(order);
    this.announceMove(order[event.currentIndex]);
  }

  protected toggle(id: string): void {
    const hidden = new Set(this.draftHidden());
    const hide = !hidden.has(id);
    if (hide) {
      hidden.add(id);
    } else {
      hidden.delete(id);
    }
    this.draftHidden.set(hidden);
    this.announce(hide ? 'dashboardLayout.hiddenNow' : 'dashboardLayout.shownNow', id);
  }

  protected reset(): void {
    this.draftOrder.set([...this.available()]);
    this.draftHidden.set(new Set());
    this.announcement.set(this.transloco.translate('dashboardLayout.resetDone'));
  }

  protected done(): void {
    const layout = this.draftLayout();
    if (JSON.stringify(layout) !== JSON.stringify(this.stored() ?? null)) {
      this.store.dispatch(
        PreferencesActions.layoutChanged({ dashboard: this.dashboard(), layout }),
      );
    }
    this.editing.set(false);
    this.announcement.set(this.transloco.translate('dashboardLayout.saved'));
  }

  protected retry(): void {
    this.store.dispatch(PreferencesActions.loadRetried());
  }

  protected label(id: string): string {
    return this.transloco.translate(this.labels() + id);
  }

  private draftLayout(): DashboardLayout | null {
    const kept = (this.stored()?.hidden ?? []).filter((id) => !this.isAvailable(id));
    const hidden = [...this.draftHidden(), ...kept];
    const order = this.draftOrder();
    const unchanged = order.every((id, index) => id === this.available()[index]);
    return unchanged && hidden.length === 0 ? null : { order, hidden };
  }

  private isAvailable(id: string): boolean {
    return this.available().includes(id);
  }

  private announceMove(id: string): void {
    this.announcement.set(
      this.transloco.translate('dashboardLayout.moved', {
        widget: this.label(id),
        position: this.draftOrder().indexOf(id) + 1,
        total: this.draftOrder().length,
      }),
    );
  }

  private announce(key: string, id: string): void {
    this.announcement.set(this.transloco.translate(key, { widget: this.label(id) }));
  }

  private keepFocus(id: string, step: -1 | 1): void {
    afterNextRender(
      () => {
        const direction = step < 0 ? 'up' : 'down';
        const other = step < 0 ? 'down' : 'up';
        const find = (move: string): HTMLButtonElement | null =>
          this.host.nativeElement.querySelector(
            `button[data-widget="${id}"][data-move="${move}"]:not([disabled])`,
          );
        (find(direction) ?? find(other))?.focus();
      },
      { injector: this.injector },
    );
  }
}
