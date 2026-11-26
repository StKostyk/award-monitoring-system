import { Provider } from '@angular/core';
import type { EChartsCoreOption } from 'echarts/core';
import { NGX_ECHARTS_CONFIG } from 'ngx-echarts';
import { vi } from 'vitest';

/** A stand-in for ECharts in unit tests: records the options and the init arguments, draws nothing. */
export interface ChartStub {
  options: EChartsCoreOption[];
  initOpts: unknown[];
  provider: Provider;
}

/** Waits for the directive's deferred chart creation and the first options. */
export async function chartsDrawn(): Promise<void> {
  await new Promise((resolve) => setTimeout(resolve));
  await Promise.resolve();
}

export function chartStub(): ChartStub {
  const stub: ChartStub = { options: [], initOpts: [], provider: [] };
  const chart = {
    setOption: (option: EChartsCoreOption) => stub.options.push(option),
    on: vi.fn(),
    off: vi.fn(),
    resize: vi.fn(),
    dispose: vi.fn(),
    isDisposed: () => false,
    showLoading: vi.fn(),
    hideLoading: vi.fn(),
  };
  stub.provider = {
    provide: NGX_ECHARTS_CONFIG,
    useValue: {
      echarts: {
        init: (_dom: unknown, _theme: unknown, opts: unknown) => {
          stub.initOpts.push(opts);
          return chart;
        },
      },
    },
  };
  vi.stubGlobal(
    'ResizeObserver',
    class {
      observe = vi.fn();
      unobserve = vi.fn();
      disconnect = vi.fn();
    },
  );
  return stub;
}
