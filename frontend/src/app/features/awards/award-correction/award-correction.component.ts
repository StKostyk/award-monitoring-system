import { HttpErrorResponse, HttpStatusCode } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatAnchor, MatButton } from '@angular/material/button';
import {
  MatDatepicker,
  MatDatepickerInput,
  MatDatepickerIntl,
  MatDatepickerToggle,
} from '@angular/material/datepicker';
import { MatDialog } from '@angular/material/dialog';
import { MatError, MatFormField, MatHint, MatLabel, MatSuffix } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatProgressBar } from '@angular/material/progress-bar';
import { MatOption, MatSelect } from '@angular/material/select';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { Observable, filter, forkJoin, map, startWith, switchMap, tap } from 'rxjs';

import { fieldProblems, problemStatus, problemType } from '../../../core/api/problem';
import { AuthService } from '../../../core/auth/auth.service';
import { LanguageService } from '../../../core/i18n/language.service';
import { kyivToday, yearsBefore } from '../../../shared/date-format';
import { organizationName } from '../../../shared/organization-name';
import { TranslatedDatepickerIntl } from '../../../shared/translated-datepicker-intl';
import { ReviewItem, ReviewsService, UserRef } from '../../reviews/reviews.service';
import { AwardDocumentsComponent } from '../award-documents/award-documents.component';
import {
  DESCRIPTION_LIMIT,
  ORGANIZATION_LIMIT,
  TITLE_LIMIT,
  URL_LIMIT,
  WEB_LINK,
  text,
  titleInOneLanguage,
} from '../award-fields';
import { FIELD_LABELS, ValueNames, shownValue } from '../award-history/version-values';
import { LeavesUnsavedChanges } from '../awards.guards';
import {
  Award,
  AwardCorrection,
  AwardForm,
  AwardsService,
  CategoryNode,
  CategoryRef,
  CorrectableField,
  MAX_AGE_YEARS,
  categoryName,
  flattenCategories,
} from '../awards.service';
import { confirmAction } from '../confirm-dialog/confirm-dialog.component';
import {
  CorrectionDialogComponent,
  CorrectionDialogData,
  ShownChange,
} from './correction-dialog.component';

/** Longest reason, as the API accepts it. */
export const REASON_LIMIT = 1000;
/** The correctable fields in the order of the form. */
const FIELDS: CorrectableField[] = [
  'titleUk',
  'title',
  'categoryId',
  'awardingOrganization',
  'awardDate',
  'descriptionUk',
  'description',
  'externalUrl',
];
const KNOWN_PROBLEMS = [
  'validation-failed',
  'award-incomplete',
  'no-change',
  'award-stale',
  'request-stale',
  'network',
];

type Values = Pick<AwardForm, CorrectableField>;

/**
 * A reviewer's correction of a pending award: the fields of the award form with their current values, the
 * recipient and documents read-only, a required reason, and a confirmation of the changes before sending.
 */
@Component({
  selector: 'app-award-correction',
  imports: [
    ReactiveFormsModule,
    MatFormField,
    MatLabel,
    MatHint,
    MatError,
    MatInput,
    MatDatepicker,
    MatDatepickerInput,
    MatDatepickerToggle,
    MatSuffix,
    MatSelect,
    MatOption,
    MatAnchor,
    MatButton,
    MatProgressBar,
    RouterLink,
    TranslocoPipe,
    AwardDocumentsComponent,
  ],
  providers: [{ provide: MatDatepickerIntl, useClass: TranslatedDatepickerIntl }],
  templateUrl: './award-correction.component.html',
  styleUrl: './award-correction.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AwardCorrectionComponent implements OnInit, LeavesUnsavedChanges {
  private readonly fb = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);
  private readonly service = inject(AwardsService);
  private readonly reviews = inject(ReviewsService);
  private readonly auth = inject(AuthService);
  private readonly language = inject(LanguageService);

  private leaving = false;

  readonly limits = {
    title: TITLE_LIMIT,
    description: DESCRIPTION_LIMIT,
    organization: ORGANIZATION_LIMIT,
    url: URL_LIMIT,
    reason: REASON_LIMIT,
  };
  readonly id = signal(0);
  readonly award = signal<Award | null>(null);
  readonly item = signal<ReviewItem | null>(null);
  readonly original = signal<Values | null>(null);
  readonly categories = signal<{ category: CategoryNode; depth: number }[]>([]);
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly unavailable = signal(false);
  readonly heldBy = signal<UserRef | null>(null);
  readonly problem = signal<string | null>(null);
  readonly stale = signal(false);

  readonly form = this.fb.group(
    {
      title: ['', Validators.maxLength(TITLE_LIMIT)],
      titleUk: ['', Validators.maxLength(TITLE_LIMIT)],
      description: ['', Validators.maxLength(DESCRIPTION_LIMIT)],
      descriptionUk: ['', Validators.maxLength(DESCRIPTION_LIMIT)],
      categoryId: [null as number | null],
      awardingOrganization: ['', Validators.maxLength(ORGANIZATION_LIMIT)],
      awardDate: [''],
      externalUrl: ['', [Validators.maxLength(URL_LIMIT), Validators.pattern(WEB_LINK)]],
      reason: [
        '',
        [Validators.required, Validators.pattern(/\S/), Validators.maxLength(REASON_LIMIT)],
      ],
    },
    { validators: titleInOneLanguage },
  );

  private readonly formValue = toSignal(
    this.form.valueChanges.pipe(
      startWith(null),
      map(() => this.values()),
    ),
    { requireSync: true },
  );

  /** The fields that differ from the award as loaded. */
  readonly changed = computed(() => {
    const original = this.original();
    const current = this.formValue();
    return original ? FIELDS.filter((field) => original[field] !== current[field]) : [];
  });
  readonly retiredCategory = computed(() => {
    const category = this.award()?.category;
    return category && !this.categories().some((item) => item.category.id === category.id)
      ? category
      : null;
  });
  readonly recipient = computed(() => {
    const award = this.award();
    if (!award) {
      return '';
    }
    const unit = award.recipient.organization;
    return unit ? organizationName(unit, this.language.current()) : award.owner.name;
  });

  /** Today on the Kyiv calendar. */
  get today(): string {
    return kyivToday();
  }

  /** The oldest award date accepted. */
  get oldest(): string {
    return yearsBefore(this.today, MAX_AGE_YEARS);
  }

  ngOnInit(): void {
    this.service.categories().subscribe({
      next: (tree) => this.categories.set(flattenCategories(tree)),
      error: () => this.categories.set([]),
    });
    const param = this.route.snapshot.paramMap.get('id') ?? '';
    if (!/^\d+$/.test(param)) {
      this.unavailable.set(true);
      return;
    }
    this.id.set(Number(param));
    this.load();
  }

  confirmLeave(): boolean | Observable<boolean> {
    return !this.form.dirty || this.leaving ? true : confirmAction(this.dialog, 'awards.leave');
  }

  reload(): void {
    this.load();
  }

  save(): void {
    const item = this.item();
    const award = this.award();
    if (!item || !award || this.changed().length === 0) {
      return;
    }
    for (const control of Object.values(this.form.controls)) {
      if (control.hasError('server')) {
        control.updateValueAndValidity({ emitEvent: false });
      }
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const reason = text(this.form.controls.reason.value) ?? '';
    const data: CorrectionDialogData = { changes: this.shownChanges(), reason };
    this.dialog
      .open<CorrectionDialogComponent, CorrectionDialogData, boolean>(CorrectionDialogComponent, {
        data,
        width: '520px',
        maxWidth: '95vw',
      })
      .afterClosed()
      .pipe(
        filter((confirmed) => confirmed === true),
        tap(() => this.start()),
        switchMap(() =>
          this.service.correct(award.id, this.body(award.version, item.requestVersion, reason)),
        ),
      )
      .subscribe({
        next: (outcome) => {
          this.saving.set(false);
          this.leaving = true;
          void this.router.navigate(['/awards', outcome.award.id], {
            replaceUrl: true,
            state: { notice: 'awards.correction.done' },
          });
        },
        error: (error: unknown) => this.failed(error),
      });
  }

  errorKey(field: CorrectableField | 'reason'): string | null {
    const errors = this.form.controls[field].errors;
    if (!errors) {
      return null;
    }
    if (errors['server']) {
      return `awards.errors.${errors['server']}`;
    }
    if (errors['required']) {
      return field === 'reason' ? 'awards.correction.reasonRequired' : 'awards.errors.required';
    }
    if (field === 'reason' && errors['pattern']) {
      return 'awards.correction.reasonRequired';
    }
    if (errors['maxlength']) {
      return 'awards.errors.too-long';
    }
    if (errors['matDatepickerParse']) {
      return 'app.dateInvalid';
    }
    if (errors['matDatepickerMax']) {
      return 'awards.errors.future';
    }
    if (errors['matDatepickerMin']) {
      return 'awards.errors.too-old';
    }
    return errors['pattern'] ? 'awards.errors.invalid' : null;
  }

  optionName(category: CategoryRef): string {
    return categoryName(category, this.language.current());
  }

  private load(): void {
    this.loading.set(true);
    this.problem.set(null);
    this.stale.set(false);
    forkJoin([this.service.get(this.id()), this.reviews.item(this.id())]).subscribe({
      next: ([award, item]) => {
        this.loading.set(false);
        if (award.status !== 'PENDING') {
          this.unavailable.set(true);
          return;
        }
        this.award.set(award);
        this.item.set(item);
        this.heldBy.set(item.reviewer && !this.holds(item.reviewer) ? item.reviewer : null);
        this.show(award);
      },
      error: () => {
        this.loading.set(false);
        this.unavailable.set(true);
      },
    });
  }

  private show(award: Award): void {
    const values: Values = {
      title: award.title ?? null,
      titleUk: award.titleUk ?? null,
      description: award.description ?? null,
      descriptionUk: award.descriptionUk ?? null,
      categoryId: award.category?.id ?? null,
      awardingOrganization: award.awardingOrganization ?? null,
      awardDate: award.awardDate ?? null,
      externalUrl: award.externalUrl ?? null,
    };
    this.original.set(values);
    this.form.reset({
      title: values.title ?? '',
      titleUk: values.titleUk ?? '',
      description: values.description ?? '',
      descriptionUk: values.descriptionUk ?? '',
      categoryId: values.categoryId,
      awardingOrganization: values.awardingOrganization ?? '',
      awardDate: values.awardDate ?? '',
      externalUrl: values.externalUrl ?? '',
      reason: this.form.controls.reason.value ?? '',
    });
  }

  private values(): Values {
    const raw = this.form.getRawValue();
    return {
      title: text(raw.title),
      titleUk: text(raw.titleUk),
      description: text(raw.description),
      descriptionUk: text(raw.descriptionUk),
      categoryId: raw.categoryId ?? null,
      awardingOrganization: text(raw.awardingOrganization),
      awardDate: text(raw.awardDate),
      externalUrl: text(raw.externalUrl),
    };
  }

  private body(version: number, requestVersion: number, reason: string): AwardCorrection {
    const values = this.values();
    const body: AwardCorrection = { version, requestVersion, reason };
    for (const field of this.changed()) {
      Object.assign(body, { [field]: values[field] });
    }
    return body;
  }

  private shownChanges(): ShownChange[] {
    const original = this.original();
    const values = this.values();
    const categories = new Map<number, CategoryRef>(
      this.categories().map(({ category }) => [category.id, category]),
    );
    const retired = this.retiredCategory();
    if (retired) {
      categories.set(retired.id, retired);
    }
    const names: ValueNames = {
      categories,
      organizations: new Map(),
      language: this.language.current(),
    };
    return this.changed().map((field) => ({
      label: FIELD_LABELS[field],
      from: shownValue(field, original?.[field] ?? null, names),
      to: shownValue(field, values[field], names),
    }));
  }

  private start(): void {
    this.saving.set(true);
    this.problem.set(null);
    this.stale.set(false);
  }

  private failed(error: unknown): void {
    this.saving.set(false);
    const type = problemType(error);
    if (problemStatus(error) === HttpStatusCode.NotFound) {
      this.unavailable.set(true);
      return;
    }
    if (type === 'request-claimed') {
      this.heldBy.set(reviewerOf(error));
      return;
    }
    if (type === 'award-stale' || type === 'request-stale') {
      this.stale.set(true);
    }
    for (const problem of fieldProblems(error)) {
      const control = this.form.get(problem.field);
      control?.setErrors({ server: problem.code });
      control?.markAsTouched();
    }
    this.problem.set(
      `awards.correction.problems.${KNOWN_PROBLEMS.includes(type) ? type : 'unknown'}`,
    );
  }

  private holds(reviewer: UserRef): boolean {
    return String(reviewer.id) === this.auth.userId();
  }
}

function reviewerOf(error: unknown): UserRef | null {
  const body = error instanceof HttpErrorResponse ? (error.error as { reviewer?: UserRef }) : null;
  return body?.reviewer ?? null;
}
