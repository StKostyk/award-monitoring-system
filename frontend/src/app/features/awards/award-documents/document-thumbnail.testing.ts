import { Component, Type, input, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { DocumentThumbnailComponent, ThumbnailSize } from './document-thumbnail.component';

/** Stands in for the thumbnail, which reads the file and draws it, in specs of the screens that list documents. */
@Component({
  selector: 'app-document-thumbnail',
  template: `<span data-testid="thumbnail-stub">{{ documentId() }} {{ mimeType() }}</span>`,
})
export class ThumbnailStubComponent {
  readonly documentId = input.required<number>();
  readonly fileName = input.required<string>();
  readonly mimeType = input.required<string>();
  readonly size = input<ThumbnailSize>('small');
  readonly ready = signal(true);
  opened = 0;

  open(): void {
    this.opened++;
  }
}

/**
 * Replaces the thumbnail in a component that lists documents; call before `compileComponents`.
 *
 * @param host the component whose template shows thumbnails
 */
export function stubThumbnails(host: Type<unknown>): void {
  TestBed.overrideComponent(host, {
    remove: { imports: [DocumentThumbnailComponent] },
    add: { imports: [ThumbnailStubComponent] },
  });
}
