import { Component, DestroyRef } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, RouterOutlet, provideRouter } from '@angular/router';
import { vi } from 'vitest';

import { NavigationFocusService } from './navigation-focus.service';

@Component({ template: '<h1>Мої нагороди</h1><button type="button">Додати</button>' })
class ListComponent {}

@Component({ template: '<p>Без заголовка</p>' })
class PlainComponent {}

@Component({
  imports: [RouterOutlet],
  template: '<button type="button" id="link">Link</button><main #main><router-outlet /></main>',
})
class HostComponent {}

describe('NavigationFocusService', () => {
  let router: Router;
  let main: HTMLElement;

  beforeEach(async () => {
    vi.useRealTimers();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'awards', component: ListComponent },
          { path: 'profile', component: ListComponent },
          { path: 'plain', component: PlainComponent },
        ]),
      ],
    });
    const fixture = TestBed.createComponent(HostComponent);
    document.body.appendChild(fixture.nativeElement);
    main = fixture.nativeElement.querySelector('main');
    TestBed.inject(NavigationFocusService).follow(
      () => main,
      fixture.componentRef.injector.get(DestroyRef),
    );
    router = TestBed.inject(Router);
    await router.navigateByUrl('/awards');
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('#link') as HTMLElement).focus();
  });

  async function settle(): Promise<void> {
    TestBed.tick();
    await new Promise((resolve) => setTimeout(resolve));
  }

  it('ac1_4_the_first_load_leaves_the_focus_alone', () => {
    expect(document.activeElement?.id).toBe('link');
  });

  it('ac1_4_a_navigation_moves_the_focus_to_the_page_heading', async () => {
    await router.navigateByUrl('/profile');
    await settle();

    const heading = main.querySelector('h1');
    expect(document.activeElement).toBe(heading);
    expect(heading?.getAttribute('tabindex')).toBe('-1');
  });

  it('ac1_4_a_change_of_the_query_only_keeps_the_focus', async () => {
    await router.navigateByUrl('/awards?page=2');
    await settle();

    expect(document.activeElement?.id).toBe('link');
  });

  it('ac1_4_a_focus_the_user_moved_is_never_taken_back', async () => {
    vi.useFakeTimers();
    await router.navigateByUrl('/plain');
    const field = document.createElement('input');
    main.appendChild(field);
    field.focus();
    vi.advanceTimersByTime(3000);
    vi.useRealTimers();

    expect(document.activeElement).toBe(field);
  });

  it('ac1_4_a_page_without_a_heading_focuses_the_main_content', async () => {
    vi.useFakeTimers();
    await router.navigateByUrl('/plain');
    vi.advanceTimersByTime(3000);
    vi.useRealTimers();

    expect(document.activeElement).toBe(main);
  });
});
