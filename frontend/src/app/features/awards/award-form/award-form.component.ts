import { Location } from '@angular/common';
import { HttpStatusCode } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  HostListener,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { MatButton } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatError, MatFormField, MatHint, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatProgressBar } from '@angular/material/progress-bar';
import { MatOption, MatSelect } from '@angular/material/select';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { Observable, debounceTime, map, switchMap, tap } from 'rxjs';

import { problemStatus, problemType } from '../../../core/api/problem';
import { AuthService } from '../../../core/auth/auth.service';
import { LanguageService } from '../../../core/i18n/language.service';
import { FormCopiesService } from '../../../core/storage/form-copies.service';
import { LeavesUnsavedChanges } from '../awards.guards';
import {
  Award,
  AwardForm,
  AwardsService,
  CategoryNode,
  CategoryRef,
  DuplicateMatch,
  MAX_AGE_YEARS,
  awardTitle,
  categoryName,
  duplicateMatches,
  fieldProblems,
  flattenCategories,
  isRecent,
  kyivToday,
  yearsBefore,
} from '../awards.service';
import { ConfirmDialogComponent } from '../confirm-dialog/confirm-dialog.component';
import { DuplicateDialogComponent } from '../duplicate-dialog/duplicate-dialog.component';

const COPY_DEBOUNCE = 400;
const TITLE_LIMIT = 500;
const DESCRIPTION_LIMIT = 4000;
const ORGANIZATION_LIMIT = 255;
const URL_LIMIT = 2048;
const KNOWN_PROBLEMS = [
  'validation-failed',
  'award-incomplete',
  'award-stale',
  'award-not-editable',
  'award-possible-duplicate',
  'access-denied',
  'network',
];
const SUBMISSION_FIELDS = ['categoryId', 'awardingOrganization', 'awardDate'] as const;

type FieldName = keyof AwardForm;

@Component({
  selector: 'app-award-form',
  imports: [
    ReactiveFormsModule,
    MatFormField,
    MatLabel,
    MatHint,
    MatError,
    MatInput,
    MatSelect,
    MatOption,
    MatButton,
    MatProgressBar,
    RouterLink,
    TranslocoPipe,
  ],
  templateUrl: './award-form.component.html',
  styleUrl: './award-form.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AwardFormComponent implements OnInit, LeavesUnsavedChanges {
  private readonly fb = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly location = inject(Location);
  private readonly dialog = inject(MatDialog);
  private readonly service = inject(AwardsService);
  private readonly auth = inject(AuthService);
  private readonly copies = inject(FormCopiesService);
  private readonly language = inject(LanguageService);

  private id: number | null = null;
  private version = 0;
  private leaving = false;

  readonly today = kyivToday();
  readonly oldest = yearsBefore(this.today, MAX_AGE_YEARS);
  readonly recentDate = signal(false);
  readonly limits = {
    title: TITLE_LIMIT,
    description: DESCRIPTION_LIMIT,
    organization: ORGANIZATION_LIMIT,
    url: URL_LIMIT,
  };
  readonly categories = signal<{ category: CategoryNode; depth: number }[]>([]);
  readonly current = signal<Award | null>(null);
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly notFound = signal(false);
  readonly message = signal<string | null>(null);
  readonly problem = signal<string | null>(null);
  readonly stale = signal(false);
  readonly restoreOffer = signal<AwardForm | null>(null);
  readonly editing = computed(() => this.current() !== null);
  readonly duplicates = computed(
    () => this.current()?.warnings.find((warning) => warning.code === 'POSSIBLE_DUPLICATE')?.matches ?? [],
  );
  readonly retiredCategory = computed(() => {
    const category = this.current()?.category;
    return category && !this.categories().some((item) => item.category.id === category.id)
      ? category
      : null;
  });

  readonly form = this.fb.group(
    {
      title: ['', Validators.maxLength(TITLE_LIMIT)],
      titleUk: ['', Validators.maxLength(TITLE_LIMIT)],
      description: ['', Validators.maxLength(DESCRIPTION_LIMIT)],
      descriptionUk: ['', Validators.maxLength(DESCRIPTION_LIMIT)],
      categoryId: [null as number | null],
      awardingOrganization: ['', Validators.maxLength(ORGANIZATION_LIMIT)],
      awardDate: [''],
      externalUrl: ['', [Validators.maxLength(URL_LIMIT), Validators.pattern(/^https?:\/\/\S+$/i)]],
    },
    { validators: titleInOneLanguage },
  );

  constructor() {
    this.form.valueChanges
      .pipe(debounceTime(COPY_DEBOUNCE), takeUntilDestroyed())
      .subscribe(() => this.keepCopy());
    this.form.controls.awardDate.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe((date) => this.recentDate.set(isRecent(date, this.today)));
  }

  ngOnInit(): void {
    this.service.categories().subscribe({
      next: (tree) => this.categories.set(flattenCategories(tree)),
      error: () => this.categories.set([]),
    });
    const param = this.route.snapshot.paramMap.get('id');
    if (param === null) {
      this.offerCopy();
      return;
    }
    if (!/^\d+$/.test(param)) {
      this.notFound.set(true);
      return;
    }
    this.id = Number(param);
    this.load(this.id);
  }

  @HostListener('window:beforeunload', ['$event'])
  keepOnUnload(event: BeforeUnloadEvent): void {
    if (this.form.dirty && !this.leaving) {
      this.keepCopy();
      event.preventDefault();
    }
  }

  confirmLeave(): boolean | Observable<boolean> {
    if (!this.form.dirty || this.leaving) {
      return true;
    }
    return this.dialog
      .open(ConfirmDialogComponent, {
        data: {
          title: 'awards.leave.title',
          text: 'awards.leave.text',
          confirm: 'awards.leave.confirm',
          cancel: 'awards.leave.cancel',
        },
        width: '420px',
      })
      .afterClosed()
      .pipe(
        map((confirmed?: boolean) => {
          if (confirmed) {
            this.dropCopy();
          }
          return confirmed === true;
        }),
      );
  }

  restore(): void {
    const copy = this.restoreOffer();
    if (copy) {
      this.form.patchValue(copy);
      this.form.markAsDirty();
      this.form.markAllAsTouched();
    }
    this.restoreOffer.set(null);
  }

  discard(): void {
    this.dropCopy();
    this.restoreOffer.set(null);
  }

  reload(): void {
    if (this.id !== null) {
      this.keepCopy();
      this.load(this.id);
    }
  }

  save(): void {
    if (this.saving()) {
      return;
    }
    this.clearServerErrors();
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.start();
    this.store().subscribe({
      next: (award) => this.finish(award, 'awards.messages.saved'),
      error: (error: unknown) => this.failed(error),
    });
  }

  submit(): void {
    if (this.saving()) {
      return;
    }
    this.clearServerErrors();
    const missing = SUBMISSION_FIELDS.filter((field) => empty(this.form.controls[field].value));
    missing.forEach((field) => this.form.controls[field].setErrors({ required: true }));
    if (this.form.invalid || missing.length) {
      this.form.markAllAsTouched();
      this.problem.set(missing.length ? 'awards.problems.award-incomplete' : null);
      return;
    }
    this.start();
    this.store()
      .pipe(switchMap((award) => this.service.submit(award.id, award.version)))
      .subscribe({
        next: (award) => this.submitted(award),
        error: (error: unknown) => this.submitFailed(error),
      });
  }

  errorKey(field: FieldName): string | null {
    const errors = this.form.controls[field].errors;
    if (!errors) {
      return null;
    }
    if (errors['server']) {
      return `awards.errors.${errors['server']}`;
    }
    if (errors['required']) {
      return 'awards.errors.required';
    }
    if (errors['maxlength']) {
      return 'awards.errors.too-long';
    }
    return errors['pattern'] ? 'awards.errors.invalid' : null;
  }

  optionName(category: CategoryRef): string {
    return categoryName(category, this.language.current());
  }

  matchTitle(match: DuplicateMatch): string {
    return awardTitle(match, this.language.current());
  }

  private submitted(award: Award): void {
    this.finish(award, null);
    this.leaving = true;
    void this.router.navigate(['/awards', award.id, 'submitted'], { replaceUrl: true });
  }

  private submitFailed(error: unknown): void {
    const matches = duplicateMatches(error);
    if (problemType(error) !== 'award-possible-duplicate' || !matches.length || this.id === null) {
      this.failed(error);
      return;
    }
    const id = this.id;
    this.saving.set(false);
    this.dropCopy();
    this.dialog
      .open(DuplicateDialogComponent, { data: { matches }, width: '480px' })
      .afterClosed()
      .subscribe((confirmed?: boolean) => {
        if (!confirmed) {
          this.message.set('awards.messages.saved');
          return;
        }
        this.start();
        this.service.submit(id, this.version, true).subscribe({
          next: (award) => this.submitted(award),
          error: (failure: unknown) => this.failed(failure),
        });
      });
  }

  private load(id: number): void {
    this.loading.set(true);
    this.service.get(id).subscribe({
      next: (award) => {
        this.loading.set(false);
        if (award.status !== 'DRAFT') {
          this.leaving = true;
          void this.router.navigate(['/awards', award.id], { replaceUrl: true });
          return;
        }
        this.show(award);
        this.stale.set(false);
        this.problem.set(null);
        this.offerCopy();
      },
      error: (error: unknown) => {
        this.loading.set(false);
        if (problemStatus(error) === HttpStatusCode.NotFound) {
          this.notFound.set(true);
        } else {
          this.problem.set(this.problemKey(error));
        }
      },
    });
  }

  private store(): Observable<Award> {
    const value = this.value();
    const request = this.id === null
      ? this.service.create(value)
      : this.service.update(this.id, value, this.version);
    return request.pipe(
      tap((award) => {
        if (this.id === null) {
          this.dropCopy();
          this.id = award.id;
          this.location.replaceState(`/awards/${award.id}/edit`);
        }
        this.show(award);
      }),
    );
  }

  private show(award: Award): void {
    this.current.set(award);
    this.version = award.version;
    this.form.reset({
      title: award.title ?? '',
      titleUk: award.titleUk ?? '',
      description: award.description ?? '',
      descriptionUk: award.descriptionUk ?? '',
      categoryId: award.category?.id ?? null,
      awardingOrganization: award.awardingOrganization ?? '',
      awardDate: award.awardDate ?? '',
      externalUrl: award.externalUrl ?? '',
    });
  }

  private start(): void {
    this.saving.set(true);
    this.message.set(null);
    this.problem.set(null);
  }

  private finish(award: Award, message: string | null): void {
    this.saving.set(false);
    this.dropCopy();
    this.message.set(message);
    this.form.markAsPristine();
    this.current.set(award);
  }

  private failed(error: unknown): void {
    this.saving.set(false);
    const type = problemType(error);
    if (type === 'award-not-editable' && this.id !== null) {
      this.dropCopy();
      this.leaving = true;
      void this.router.navigate(['/awards', this.id], { state: { problem: type } });
      return;
    }
    if (type === 'award-stale') {
      this.keepCopy(true);
      this.stale.set(true);
    }
    for (const problem of fieldProblems(error)) {
      const control = this.form.get(problem.field);
      control?.setErrors({ server: problem.code });
      control?.markAsTouched();
    }
    this.problem.set(this.problemKey(error));
  }

  private problemKey(error: unknown): string {
    const type = problemType(error);
    return `awards.problems.${KNOWN_PROBLEMS.includes(type) ? type : 'unknown'}`;
  }

  private clearServerErrors(): void {
    for (const control of Object.values(this.form.controls)) {
      if (control.hasError('server')) {
        control.setErrors(null);
        control.updateValueAndValidity({ emitEvent: false });
      }
    }
  }

  private value(): AwardForm {
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

  private offerCopy(): void {
    const userId = this.auth.userId();
    const copy = userId ? this.copies.load<AwardForm>(userId, this.copyName()) : null;
    const differs = copy && JSON.stringify(copy) !== JSON.stringify(this.value());
    this.restoreOffer.set(differs ? copy : null);
  }

  private keepCopy(evenIfPristine = false): void {
    const userId = this.auth.userId();
    if (userId && (this.form.dirty || evenIfPristine) && !this.leaving) {
      this.copies.save(userId, this.copyName(), this.value());
    }
  }

  private dropCopy(): void {
    const userId = this.auth.userId();
    if (userId) {
      this.copies.remove(userId, this.copyName());
    }
  }

  private copyName(): string {
    return this.id === null ? 'award-new' : `award-${this.id}`;
  }
}

function titleInOneLanguage(group: AbstractControl): ValidationErrors | null {
  const title = group.get('title')?.value as string | null;
  const titleUk = group.get('titleUk')?.value as string | null;
  return empty(title) && empty(titleUk) ? { titleRequired: true } : null;
}

function empty(value: unknown): boolean {
  return value === null || value === undefined || String(value).trim() === '';
}

function text(value: string | null | undefined): string | null {
  const trimmed = value?.trim() ?? '';
  return trimmed === '' ? null : trimmed;
}
