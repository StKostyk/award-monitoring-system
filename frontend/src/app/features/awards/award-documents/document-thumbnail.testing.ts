import { Component, Type, input } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { DocumentThumbnailComponent } from './document-thumbnail.component';

/** Stands in for the thumbnail, which reads the file and draws it, in specs of the screens that list documents. */
@Component({
  selector: 'app-document-thumbnail',
  template: `<span data-testid="thumbnail-stub">{{ documentId() }} {{ mimeType() }}</span>`,
})
export class ThumbnailStubComponent {
  readonly documentId = input.required<number>();
  readonly fileName = input.required<string>();
  readonly mimeType = input.required<string>();
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
