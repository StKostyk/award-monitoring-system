import { Directive, TemplateRef, booleanAttribute, inject, input } from '@angular/core';

/** One widget of a dashboard: its name in the dashboard's widget set and whether it spans the full row. */
@Directive({ selector: 'ng-template[appDashboardWidget]' })
export class DashboardWidgetDirective {
  readonly id = input.required<string>({ alias: 'appDashboardWidget' });
  readonly wide = input(false, { transform: booleanAttribute });
  readonly template = inject(TemplateRef);
}
