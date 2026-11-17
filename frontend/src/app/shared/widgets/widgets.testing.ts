import { Type } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';

import { languageStub, translations } from '../../features/analytics/analytics.testing';
import { ChartStub, chartStub, chartsDrawn } from '../charts/chart.testing';

/** A widget rendered in Ukrainian with the given inputs, charts recorded by a stub. */
export async function renderWidget<T>(
  component: Type<T>,
  inputs: Record<string, unknown>,
): Promise<{ fixture: ComponentFixture<T>; stub: ChartStub; text: () => string }> {
  const stub = chartStub();
  TestBed.configureTestingModule({
    imports: [component, NoopAnimationsModule, translations('uk')],
    providers: [stub.provider, languageStub('uk').provider, provideRouter([])],
  });
  const fixture = TestBed.createComponent(component);
  for (const [name, value] of Object.entries(inputs)) {
    fixture.componentRef.setInput(name, value);
  }
  fixture.detectChanges();
  await chartsDrawn();
  fixture.detectChanges();
  const host = fixture.nativeElement as HTMLElement;
  return { fixture, stub, text: () => host.textContent?.replace(/\s+/g, ' ').trim() ?? '' };
}

/** The element of the fixture with the given test id. */
export function byTestId(fixture: ComponentFixture<unknown>, testId: string): HTMLElement | null {
  return (fixture.nativeElement as HTMLElement).querySelector(`[data-testid="${testId}"]`);
}

/** Turns the «Дані» switch of a chart on and returns the projected table. */
export function showData(fixture: ComponentFixture<unknown>): HTMLElement | null {
  byTestId(fixture, 'chart-table-switch')!.querySelector('button')!.click();
  fixture.detectChanges();
  return byTestId(fixture, 'chart-table');
}

/** The last options the chart stub received, read as the given shape. */
export function lastOption<T>(stub: ChartStub): T {
  return stub.options.at(-1) as unknown as T;
}

/** The rows of a table body, each its cells' trimmed text joined by spaces. */
export function rowTexts(table: HTMLElement): string[] {
  return Array.from(table.querySelectorAll('tbody tr'), (row) =>
    Array.from(row.children, (cell) => cell.textContent!.trim())
      .join(' ')
      .trim(),
  );
}
