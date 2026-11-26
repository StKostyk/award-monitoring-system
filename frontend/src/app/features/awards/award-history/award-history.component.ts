import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatProgressBar } from '@angular/material/progress-bar';
import { TranslocoPipe } from '@jsverse/transloco';

import { LanguageService } from '../../../core/i18n/language.service';
import { kyivDateTime } from '../../../shared/date-format';
import { PagedList } from '../../../shared/paged-list';
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

  private readonly list = new PagedList<AwardVersion, number>({
    fetch: (page) => this.service.versions(this.award().id, page, PAGE_SIZE),
    key: (version) => version.number,
    loaded: (versions) => this.resolveOrganizations(versions),
  });
  private readonly categories = signal(new Map<number, CategoryRef>());
  private readonly organizations = signal(new Map<number, OrganizationName>());

  readonly versions = this.list.items;
  readonly loading = this.list.loading;
  readonly problem = this.list.problem;
  readonly hasMore = this.list.hasMore;

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
    this.list.load();
  }

  /** Shows the newest versions again, after the award changed while the page was open. */
  reload(): void {
    this.list.reload();
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
