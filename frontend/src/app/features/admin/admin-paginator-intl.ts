import { Injectable, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatPaginatorIntl } from '@angular/material/paginator';
import { TranslocoService } from '@jsverse/transloco';

/** Paginator labels in the active language. */
@Injectable()
export class AdminPaginatorIntl extends MatPaginatorIntl {
  private readonly transloco = inject(TranslocoService);

  constructor() {
    super();
    this.transloco
      .selectTranslate('admin.users.paginator.itemsPerPage')
      .pipe(takeUntilDestroyed())
      .subscribe((label: string) => this.relabel(label));
  }

  override getRangeLabel = (page: number, pageSize: number, length: number): string =>
    this.transloco.translate('admin.users.paginator.range', {
      from: length === 0 ? 0 : page * pageSize + 1,
      to: Math.min((page + 1) * pageSize, length),
      total: length,
    });

  private relabel(itemsPerPage: string): void {
    this.itemsPerPageLabel = itemsPerPage;
    this.firstPageLabel = this.transloco.translate('admin.users.paginator.first');
    this.previousPageLabel = this.transloco.translate('admin.users.paginator.previous');
    this.nextPageLabel = this.transloco.translate('admin.users.paginator.next');
    this.lastPageLabel = this.transloco.translate('admin.users.paginator.last');
    this.changes.next();
  }
}
