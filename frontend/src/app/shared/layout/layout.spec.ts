import { ApplicationRef, Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';

import { BentoComponent, BentoTileComponent } from './bento.component';
import { ListRowComponent, ListRowsDirective } from './list-row.component';
import { PageHeaderComponent } from './page-header.component';
import { PaneComponent } from './pane.component';
import { SplitComponent } from './split.component';
import { YearGroupComponent } from './year-group.component';

@Component({
  imports: [
    BentoComponent,
    BentoTileComponent,
    ListRowComponent,
    ListRowsDirective,
    PageHeaderComponent,
    PaneComponent,
    SplitComponent,
    YearGroupComponent,
  ],
  template: `
    <app-page-header context="Faculty of Mathematics · 2026" heading="Reviews">
      <span pageChips data-testid="chip">3 waiting</span>
      <button pageActions type="button" data-testid="action">Export</button>
    </app-page-header>
    <app-split mode="detail" [detailOpen]="selected() !== null">
      <div splitSide appListRows data-testid="rows">
        @for (row of rows; track row.id) {
          <button
            type="button"
            appListRow
            [initials]="row.initials"
            [rowTitle]="row.title"
            [meta]="row.meta"
            [selected]="selected() === row.id"
            (click)="selected.set(row.id)"
          >
            <span rowEnd>chip</span>
          </button>
        }
        <input data-testid="filter" />
      </div>
      <app-pane splitMain heading="Detail" data-testid="detail">body</app-pane>
    </app-split>
    <app-bento>
      <app-bento-tile [span]="4" data-testid="wide">a</app-bento-tile>
      <app-bento-tile data-testid="narrow">b</app-bento-tile>
      <app-bento-tile [span]="2" [rows]="2">c</app-bento-tile>
    </app-bento>
    <app-year-group heading="2025">
      <p>award</p>
    </app-year-group>
  `,
})
class HostComponent {
  readonly rows = [
    { id: 1, initials: 'ОК', title: 'Олена Коваль', meta: 'Кафедра алгебри' },
    { id: 2, initials: 'ІП', title: 'Іван Петренко', meta: 'Кафедра геометрії' },
    { id: 3, initials: 'МС', title: 'Марія Савчук', meta: 'Кафедра фізики' },
  ];
  readonly selected = signal<number | null>(null);
}

const KEY_CODES: Record<string, number> = { End: 35, Home: 36, ArrowUp: 38, ArrowDown: 40 };

describe('page layout kit', () => {
  afterEach(() => vi.restoreAllMocks());

  function render(): { element: HTMLElement; host: HostComponent; detect: () => void } {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    return {
      element: fixture.nativeElement as HTMLElement,
      host: fixture.componentInstance,
      detect: () => fixture.detectChanges(),
    };
  }

  function rows(element: HTMLElement): HTMLButtonElement[] {
    return [...element.querySelectorAll<HTMLButtonElement>('[appListRow]')];
  }

  function narrow(split: HTMLElement): void {
    const real = window.getComputedStyle.bind(window);
    vi.spyOn(window, 'getComputedStyle').mockImplementation((target, pseudo) => {
      const hidden =
        (target as Element).matches('.split__side') &&
        split.classList.contains('split--detail-open');
      const hiddenMain =
        (target as Element).matches('.split__main') &&
        !split.classList.contains('split--detail-open');
      return hidden || hiddenMain
        ? ({ display: 'none' } as CSSStyleDeclaration)
        : real(target, pseudo);
    });
  }

  function press(target: Element, key: string): KeyboardEvent {
    const event = new KeyboardEvent('keydown', {
      key,
      keyCode: KEY_CODES[key],
      bubbles: true,
      cancelable: true,
    });
    target.dispatchEvent(event);
    return event;
  }

  it('ac1_1_page_header_shows_the_context_the_only_h1_and_the_projected_chips_and_actions', () => {
    const { element } = render();
    const header = element.querySelector('app-page-header') as HTMLElement;

    expect(element.querySelectorAll('h1')).toHaveLength(1);
    expect(header.querySelector('h1')?.textContent?.trim()).toBe('Reviews');
    expect(header.querySelector('[data-testid="page-context"]')?.textContent?.trim()).toBe(
      'Faculty of Mathematics · 2026',
    );
    const side = header.querySelector('.page-header__side') as HTMLElement;
    expect(side.querySelector('[data-testid="chip"]')).not.toBeNull();
    expect(side.querySelector('[data-testid="action"]')).not.toBeNull();
  });

  it('ac1_2_split_puts_the_side_column_first_and_hides_it_behind_an_open_detail_when_narrow', () => {
    const { element, host, detect } = render();
    const split = element.querySelector('app-split') as HTMLElement;

    const columns = [...split.querySelectorAll('.split__side, .split__main')];
    expect(columns.map((column) => column.className)).toEqual(['split__side', 'split__main']);
    expect(split.querySelector('.split__side [data-testid="rows"]')).not.toBeNull();
    expect(split.querySelector('.split__main [data-testid="detail"]')).not.toBeNull();
    expect(split.classList).toContain('split--detail');
    expect(split.classList).not.toContain('split--detail-open');

    host.selected.set(2);
    detect();
    expect(split.classList).toContain('split--detail-open');
  });

  it('ac1_2_a_narrow_detail_takes_the_focus_and_gives_it_back_to_the_selected_row', async () => {
    const { element, host, detect } = render();
    const split = element.querySelector('app-split') as HTMLElement;
    narrow(split);
    const second = rows(element)[1];
    second.focus();

    second.click();
    detect();
    await TestBed.inject(ApplicationRef).whenStable();
    expect(document.activeElement).toBe(split.querySelector('.split__main'));

    host.selected.set(null);
    detect();
    await TestBed.inject(ApplicationRef).whenStable();
    expect(document.activeElement).toBe(second);
    vi.restoreAllMocks();
  });

  it('ac10_1_back_from_a_narrow_detail_restores_the_list_scroll_and_the_row_focus', async () => {
    const { element, host, detect } = render();
    const split = element.querySelector('app-split') as HTMLElement;
    narrow(split);
    let scrollTop = 0;
    element.style.overflowY = 'auto';
    Object.defineProperty(element, 'scrollTop', {
      configurable: true,
      get: () => scrollTop,
      set: (value: number) => (scrollTop = value),
    });
    scrollTop = 500;
    const second = rows(element)[1];
    second.focus();
    second.addEventListener('click', () => (scrollTop = 0));

    second.click();
    detect();
    await TestBed.inject(ApplicationRef).whenStable();
    const heading = element.querySelector('h1') as HTMLElement;
    heading.setAttribute('tabindex', '-1');
    heading.focus();

    host.selected.set(null);
    detect();
    await TestBed.inject(ApplicationRef).whenStable();
    expect(scrollTop).toBe(500);
    expect(document.activeElement).toBe(second);
    vi.restoreAllMocks();
  });

  it('ac10_1_a_detail_opened_long_after_a_side_click_leaves_the_scroll_alone', async () => {
    const { element, host, detect } = render();
    const split = element.querySelector('app-split') as HTMLElement;
    narrow(split);
    let scrollTop = 0;
    element.style.overflowY = 'auto';
    Object.defineProperty(element, 'scrollTop', {
      configurable: true,
      get: () => scrollTop,
      set: (value: number) => (scrollTop = value),
    });

    scrollTop = 500;
    (element.querySelector('[data-testid="filter"]') as HTMLElement).click();
    const now = Date.now();
    vi.spyOn(Date, 'now').mockReturnValue(now + 3000);
    host.selected.set(3);
    detect();
    await TestBed.inject(ApplicationRef).whenStable();
    scrollTop = 120;
    host.selected.set(null);
    detect();
    await TestBed.inject(ApplicationRef).whenStable();
    expect(scrollTop).toBe(120);
    vi.restoreAllMocks();
  });

  it('ac1_2_a_wide_detail_leaves_the_focus_on_the_row', async () => {
    const { element, detect } = render();
    const second = rows(element)[1];
    second.focus();

    second.click();
    detect();
    await TestBed.inject(ApplicationRef).whenStable();
    expect(document.activeElement).toBe(second);
  });

  it('ac1_2_pane_names_its_region_after_its_heading', () => {
    const { element } = render();
    const pane = element.querySelector('app-pane') as HTMLElement;
    const section = pane.querySelector('section') as HTMLElement;

    const heading = pane.querySelector('h2') as HTMLElement;
    expect(heading.textContent?.trim()).toBe('Detail');
    expect(section.getAttribute('aria-labelledby')).toBe(heading.id);
  });

  it('ac1_3_list_row_shows_initials_title_meta_and_marks_the_selected_row', () => {
    const { element, detect } = render();
    const [first, second] = rows(element);

    expect(first.querySelector('.list-row__avatar')?.getAttribute('aria-hidden')).toBe('true');
    expect(first.querySelector('.list-row__avatar')?.textContent?.trim()).toBe('ОК');
    expect(first.querySelector('.list-row__title')?.textContent?.trim()).toBe('Олена Коваль');
    expect(first.querySelector('.list-row__meta')?.textContent?.trim()).toBe('Кафедра алгебри');
    expect(first.textContent).toContain('chip');
    expect(first.getAttribute('aria-current')).toBeNull();

    second.click();
    detect();
    expect(second.getAttribute('aria-current')).toBe('true');
    expect(first.getAttribute('aria-current')).toBeNull();
  });

  it('ac1_3_arrow_keys_move_between_rows_and_home_end_jump', () => {
    const { element } = render();
    const all = rows(element);
    all[0].focus();

    expect(press(all[0], 'ArrowDown').defaultPrevented).toBe(true);
    expect(document.activeElement).toBe(all[1]);
    press(all[1], 'ArrowDown');
    press(all[2], 'ArrowDown');
    expect(document.activeElement).toBe(all[2]);
    press(all[2], 'Home');
    expect(document.activeElement).toBe(all[0]);
    press(all[0], 'End');
    expect(document.activeElement).toBe(all[2]);
    press(all[2], 'ArrowUp');
    expect(document.activeElement).toBe(all[1]);
  });

  it('ac1_3_arrow_keys_in_a_text_field_stay_with_the_field', () => {
    const { element } = render();
    const filter = element.querySelector('[data-testid="filter"]') as HTMLInputElement;
    filter.focus();

    expect(press(filter, 'ArrowDown').defaultPrevented).toBe(false);
    expect(document.activeElement).toBe(filter);
  });

  it('ac1_3_rows_are_native_buttons_or_links_so_enter_selects', () => {
    const { element } = render();

    expect(rows(element).every((row) => row.tagName === 'BUTTON' && row.type === 'button')).toBe(
      true,
    );
  });

  it('ac1_4_bento_tiles_keep_their_order_and_carry_their_span', () => {
    const { element } = render();
    const tiles = [...element.querySelectorAll<HTMLElement>('app-bento app-bento-tile')];

    expect(tiles.map((tile) => tile.textContent?.trim())).toEqual(['a', 'b', 'c']);
    expect(tiles.map((tile) => tile.getAttribute('data-span'))).toEqual(['4', '1', '2']);
  });

  it('ac6_3_a_bento_tile_spans_two_rows_on_request', () => {
    const { element } = render();
    const tiles = [...element.querySelectorAll<HTMLElement>('app-bento app-bento-tile')];

    expect(tiles.map((tile) => tile.getAttribute('data-rows'))).toEqual(['1', '1', '2']);
  });

  it('ac1_1_year_group_heads_its_list_with_the_year', () => {
    const { element } = render();
    const group = element.querySelector('app-year-group') as HTMLElement;

    expect(group.querySelector('h2')?.textContent?.trim()).toBe('2025');
    expect(group.querySelector('p')?.textContent).toBe('award');
  });
});
