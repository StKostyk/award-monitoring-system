import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatProgressBar } from '@angular/material/progress-bar';
import { TranslocoPipe } from '@jsverse/transloco';

import { LanguageService } from '../../../core/i18n/language.service';
import {
  Award,
  AwardVersion,
  AwardsService,
  CategoryRef,
  OrganizationName,
  SnapshotField,
  flattenCategories,
} from '../awards.service';
import { AwardVersionDialogComponent, VersionDialogData } from './award-version-dialog.component';
import {
  FIELD_LABELS,
  ShownValue,
  ValueNames,
  kyivDateTime,
  organizationIds,
  shownValue,
} from './version-values';

const PAGE_SIZE = 20;

@Component({
  selector: 'app-award-history',
  imports: [MatButton, MatProgressBar, TranslocoPipe],
  templateUrl: './award-history.component.html',
  styleUrl: './award-history.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AwardHistoryComponent implements OnInit {
  private readonly service = inject(AwardsService);
  private readonly dialog = inject(MatDialog);
  private readonly language = inject(LanguageService);

  readonly award = input.required<Award>();

  readonly versions = signal<AwardVersion[]>([]);
  readonly loading = signal(false);
  readonly failed = signal(false);
  private readonly loadedPages = signal(0);
  private readonly totalPages = signal(0);
  private readonly categories = signal(new Map<number, CategoryRef>());
  private readonly organizations = signal(new Map<number, OrganizationName>());

  readonly hasMore = computed(() => this.loadedPages() < this.totalPages());

  ngOnInit(): void {
    const organization = this.award().organization;
    this.organizations.set(new Map([[organization.id, organization]]));
    this.service.categories().subscribe({
      next: (nodes) =>
        this.categories.set(
          new Map(flattenCategories(nodes).map(({ category }) => [category.id, category])),
        ),
      error: () => undefined,
    });
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.failed.set(false);
    this.service.versions(this.award().id, this.loadedPages(), PAGE_SIZE).subscribe({
      next: (page) => {
        this.versions.update((shown) => [
          ...shown,
          ...page.content.filter(
            (version) => !shown.some((known) => known.number === version.number),
          ),
        ]);
        this.loadedPages.update((pages) => pages + 1);
        this.totalPages.set(page.totalPages);
        this.loading.set(false);
        this.resolveOrganizations(page.content);
      },
      error: () => {
        this.loading.set(false);
        this.failed.set(true);
      },
    });
  }

  view(version: AwardVersion): void {
    const data: VersionDialogData = { version, names: this.names() };
    this.dialog.open(AwardVersionDialogComponent, { data, width: '560px', maxWidth: '95vw' });
  }

  label(field: SnapshotField): string {
    return FIELD_LABELS[field];
  }

  value(field: SnapshotField, value: unknown): ShownValue {
    return shownValue(field, value, this.names());
  }

  time(version: AwardVersion): string {
    return kyivDateTime(version.createdAt, this.language.current());
  }

  names(): ValueNames {
    return {
      categories: this.categories(),
      organizations: this.organizations(),
      language: this.language.current(),
    };
  }

  private resolveOrganizations(versions: AwardVersion[]): void {
    const known = this.organizations();
    if (organizationIds(versions).every((id) => known.has(id))) {
      return;
    }
    this.service.organizations().subscribe({
      next: (all) => this.organizations.update((current) => new Map([...all, ...current])),
      error: () => undefined,
    });
  }
}
