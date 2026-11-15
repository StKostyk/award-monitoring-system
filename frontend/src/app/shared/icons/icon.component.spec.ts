import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { IconComponent } from './icon.component';
import { ICONS, IconName } from './icons';

@Component({
  imports: [IconComponent],
  template: `<button type="button" aria-label="Close"><app-icon [name]="name()" /></button>`,
})
class HostComponent {
  readonly name = signal<IconName>('x');
}

describe('IconComponent', () => {
  function render() {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('ac1_4_draws_the_lucide_svg_of_the_named_icon', () => {
    const host: HTMLElement = render().nativeElement;

    const svg = host.querySelector('app-icon svg');
    expect(svg).not.toBeNull();
    expect(svg?.children.length).toBe(ICONS.x.length);
    expect(svg?.children[0].getAttribute('d')).toBe(ICONS.x[0][1]['d']);
    expect(svg?.getAttribute('stroke')).toBe('currentColor');
  });

  it('ac1_4_hides_the_icon_from_assistive_technology', () => {
    const host: HTMLElement = render().nativeElement;

    expect(host.querySelector('app-icon')?.getAttribute('aria-hidden')).toBe('true');
    expect(host.querySelector('button')?.getAttribute('aria-label')).toBe('Close');
  });

  it('ac1_4_redraws_when_the_name_changes', () => {
    const fixture = render();

    fixture.componentInstance.name.set('check');
    fixture.detectChanges();

    const host: HTMLElement = fixture.nativeElement;
    expect(host.querySelector('app-icon')?.getAttribute('data-icon')).toBe('check');
    const svg = host.querySelector('app-icon svg');
    expect(svg?.children.length).toBe(ICONS.check.length);
    expect(svg?.children[0].getAttribute('d')).toBe(ICONS.check[0][1]['d']);
  });
});
