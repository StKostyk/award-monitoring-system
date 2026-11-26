import { Provider } from '@angular/core';
import { provideEchartsCore } from 'ngx-echarts';

/** The ECharts parts a route needs: the base charts, or those plus the widget charts. */
export type ChartSet = 'base' | 'widgets';

/**
 * Provides the lazily loaded ECharts core for a route that renders `app-chart`.
 *
 * @param set the chart parts the route needs
 * @returns the provider for the route's providers
 */
export function provideAppCharts(set: ChartSet = 'base'): Provider {
  return provideEchartsCore({
    echarts:
      set === 'widgets'
        ? () => import('./echarts-widgets-setup').then((m) => m.echarts)
        : () => import('./echarts-setup').then((m) => m.echarts),
  });
}
