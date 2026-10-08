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
import { MatAnchor, MatButton } from '@angular/material/button';
import {
  MatDatepicker,
  MatDatepickerInput,
  MatDatepickerToggle,
} from '@angular/material/datepicker';
import { MatDialog } from '@angular/material/dialog';
import { MatError, MatFormField, MatHint, MatLabel, MatSuffix } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatProgressBar } from '@angular/material/progress-bar';
import { MatRadioButton, MatRadioGroup } from '@angular/material/radio';
import { MatOption, MatSelect } from '@angular/material/select';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import {
  Observable,
  catchError,
  debounceTime,
  distinctUntilChanged,
  filter,
  finalize,
  map,
  merge,
  of,
  share,
  switchMap,
  tap,
  throwError,
} from 'rxjs';

import { fieldProblems, problemStatus, problemType } from '../../../core/api/problem';
import { AuthService } from '../../../core/auth/auth.service';
import { LanguageService } from '../../../core/i18n/language.service';
import { FormCopiesService } from '../../../core/storage/form-copies.service';
import { kyivToday, yearsBefore } from '../../../shared/date-format';
import { organizationName } from '../../../shared/organization-name';
import { AwardDocumentsComponent } from '../award-documents/award-documents.component';
import { LeavesUnsavedChanges } from '../awards.guards';
import {
  Award,
  AwardForm,
  AwardsService,
  CategoryNode,
  CategoryRef,
  CategorySuggestion,
  DuplicateMatch,
  MAX_AGE_YEARS,
  SUGGESTION_MIN_LENGTH,
  UnitRef,
  awardTitle,
  categoryName,
  duplicateMatches,
  flattenCategories,
  isRecent,
} from '../awards.service';
import { confirmAction } from '../confirm-dialog/confirm-dialog.component';
import { DuplicateDialogComponent } from '../duplicate-dialog/duplicate-dialog.component';

const COPY_DEBOUNCE = 400;
const SUGGEST_DEBOUNCE = 400;
/** Characters of each text sent for suggestions; enough for the rules and short enough for a request line. */
const SUGGEST_TEXT_LIMIT = 300;
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
  'recipient-out-of-scope',
  'access-denied',
  'network',
];
const SUBMISSION_FIELDS = ['categoryId', 'awardingOrganization', 'awardDate'] as const;

type FieldName = keyof AwardForm;
type RecipientChoice = 'PERSON' | 'UNIT';

@Component({
  selector: 'app-award-form',
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
    MatRadioGroup,
    MatRadioButton,
    RouterLink,
    TranslocoPipe,
    AwardDocumentsComponent,
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
  private owner: string | null = null;
  private version = 0;
  private leaving = false;
  private running: Observable<Award> | null = null;

  readonly recentDate = signal(false);
  readonly limits = {
    title: TITLE_LIMIT,
    description: DESCRIPTION_LIMIT,
    organization: ORGANIZATION_LIMIT,
    url: URL_LIMIT,
  };
  readonly categories = signal<{ category: CategoryNode; depth: number }[]>([]);
  readonly current = signal<Award | null>(null);
  /** The reviewer's return of the draft being edited, shown until the draft is submitted again. */
  readonly returned = signal<{ comment: string | null } | null>(null);
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly notFound = signal(false);
  readonly message = signal<string | null>(null);
  readonly problem = signal<string | null>(null);
  readonly stale = signal(false);
  readonly restoreOffer = signal<AwardForm | null>(null);
  readonly suggestions = signal<CategorySuggestion[]>([]);
  readonly categoryChosen = signal(false);
  readonly documentCount = signal(0);
  readonly units = signal<UnitRef[]>([]);
  readonly unitChosen = signal(false);
  /** The recipient choice is offered to faculty secretaries and deans, and kept on a unit draft of anyone. */
  readonly unitOptions = computed(() => {
    const current = this.current()?.recipient.organization;
    return current && !this.units().some((unit) => unit.id === current.id)
      ? [current, ...this.units()]
      : this.units();
  });
  readonly editing = computed(() => this.current() !== null);
  readonly cancelLink = computed(() => {
    const award = this.current();
    return award ? ['/awards', award.id] : ['/awards'];
  });
  readonly duplicates = computed(
    () =>
      this.current()?.warnings.find((warning) => warning.code === 'POSSIBLE_DUPLICATE')?.matches ??
      [],
  );
  readonly retiredCategory = computed(() => {
    const category = this.current()?.category;
    return category && !this.categories().some((item) => item.category.id === category.id)
      ? category
      : null;
  });

  readonly form = this.fb.group(
    {
      recipient: ['PERSON' as RecipientChoice],
      recipientOrganizationId: [null as number | null],
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

  /** Today on the Kyiv calendar, read at every check so that a form left open past midnight stays right. */
  get today(): string {
    return kyivToday();
  }

  /** The oldest award date accepted. */
  get oldest(): string {
    return yearsBefore(this.today, MAX_AGE_YEARS);
  }

  constructor() {
    this.form.valueChanges
      .pipe(debounceTime(COPY_DEBOUNCE), takeUntilDestroyed())
      .subscribe(() => this.keepCopy());
    this.form.controls.awardDate.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe((date) => this.recentDate.set(isRecent(date, this.today)));
    this.form.controls.categoryId.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe((id) => this.categoryChosen.set(id !== null));
    this.form.controls.recipient.valueChanges.pipe(takeUntilDestroyed()).subscribe((choice) => {
      this.unitChosen.set(choice === 'UNIT');
      if (choice === 'PERSON') {
        this.form.controls.recipientOrganizationId.setValue(null);
      }
    });
    this.suggestionInput()
      .pipe(
        switchMap(({ title, organization }) =>
          title.length < SUGGESTION_MIN_LENGTH && organization.length < SUGGESTION_MIN_LENGTH
            ? of([])
            : this.service.suggestions(title, organization).pipe(catchError(() => of([]))),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((suggestions) => this.suggestions.set(suggestions));
  }

  ngOnInit(): void {
    this.service.categories().subscribe({
      next: (tree) => this.categories.set(flattenCategories(tree)),
      error: () => this.categories.set([]),
    });
    this.service.recipientUnits().subscribe({
      next: (units) => this.units.set(units),
      error: () => this.units.set([]),
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
      if (this.auth.isAuthenticated() && this.copies.keepsCopies()) {
        event.preventDefault();
      }
    }
  }

  confirmLeave(): boolean | Observable<boolean> {
    if (!this.form.dirty || this.leaving) {
      return true;
    }
    return confirmAction(this.dialog, 'awards.leave').pipe(
      tap((confirmed) => {
        if (confirmed) {
          this.dropCopy();
        }
      }),
    );
  }

  restore(): void {
    const copy = this.restoreOffer();
    if (copy) {
      this.form.patchValue({
        ...copy,
        recipient: copy.recipientOrganizationId ? 'UNIT' : 'PERSON',
      });
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
    this.clearMarkedErrors();
    if (this.form.invalid || this.unitMissing()) {
      this.form.markAllAsTouched();
      return;
    }
    this.start();
    const request = this.store().pipe(
      finalize(() => (this.running = null)),
      share(),
    );
    this.running = request;
    request.subscribe({
      next: (award) => this.finish(award, 'awards.messages.saved'),
      error: (error: unknown) => this.failed(error),
    });
  }

  /** Saves a new award before its first file is uploaded, exactly as «Зберегти чернетку» does. */
  readonly prepareUpload = (): Observable<number> => {
    if (this.running !== null) {
      return this.running.pipe(map((award) => award.id));
    }
    this.clearMarkedErrors();
    if (this.saving() || this.form.invalid || this.unitMissing()) {
      this.form.markAllAsTouched();
      return throwError(() => new Error('The draft cannot be saved'));
    }
    this.start();
    return this.store().pipe(
      tap({
        next: (award) => this.finish(award, 'awards.messages.saved'),
        error: (error: unknown) => this.failed(error),
      }),
      map((award) => award.id),
    );
  };

  submit(): void {
    if (this.saving()) {
      return;
    }
    this.clearMarkedErrors();
    const missing = SUBMISSION_FIELDS.filter((field) => empty(this.form.controls[field].value));
    missing.forEach((field) => this.form.controls[field].setErrors({ required: true }));
    if (this.unitMissing() || this.form.invalid || missing.length) {
      this.form.markAllAsTouched();
      this.problem.set(missing.length ? 'awards.problems.award-incomplete' : null);
      return;
    }
    if (this.documentCount() > 0) {
      this.send();
      return;
    }
    confirmAction(this.dialog, 'awards.submitWithout')
      .pipe(filter(Boolean))
      .subscribe(() => this.send());
  }

  /**
   * Takes an upload refused because the draft was deleted or submitted elsewhere; a running save meets the
   * same refusal itself.
   *
   * @param error the refusal of the upload
   */
  documentsLost(error: unknown): void {
    if (!this.saving()) {
      this.failed(error);
    }
  }

  private send(): void {
    this.start();
    this.store()
      .pipe(switchMap((award) => this.service.submit(award.id, award.version)))
      .subscribe({
        next: (award) => this.submitted(award),
        error: (error: unknown) => this.submitFailed(error),
      });
  }

  remove(): void {
    const id = this.id;
    if (id === null || this.saving()) {
      return;
    }
    confirmAction(this.dialog, 'awards.remove')
      .pipe(
        filter(Boolean),
        switchMap(() => this.startRemoval(id)),
      )
      .subscribe({
        next: () => this.removed(),
        error: (error: unknown) =>
          problemStatus(error) === HttpStatusCode.NotFound ? this.removed() : this.failed(error),
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

  choose(suggestion: CategorySuggestion): void {
    this.form.controls.categoryId.setValue(suggestion.id);
    this.form.controls.categoryId.markAsDirty();
  }

  unitName(unit: UnitRef): string {
    return organizationName(unit, this.language.current());
  }

  optionName(category: CategoryRef): string {
    return categoryName(category, this.language.current());
  }

  matchTitle(match: DuplicateMatch): string {
    return awardTitle(match, this.language.current());
  }

  /** Marks the unit picker when «Підрозділ» is chosen without a unit. */
  private unitMissing(): boolean {
    const { recipient, recipientOrganizationId } = this.form.controls;
    const missing = recipient.value === 'UNIT' && recipientOrganizationId.value === null;
    if (missing) {
      recipientOrganizationId.setErrors({ required: true });
    }
    return missing;
  }

  private startRemoval(id: number): Observable<void> {
    this.start();
    return this.service.remove(id);
  }

  private removed(): void {
    this.saving.set(false);
    this.dropCopy();
    this.leaving = true;
    void this.router.navigate(['/awards'], {
      replaceUrl: true,
      state: { notice: 'awards.messages.removed' },
    });
  }

  private suggestionInput(): Observable<{ title: string; organization: string }> {
    const { title, titleUk, awardingOrganization } = this.form.controls;
    return merge(title.valueChanges, titleUk.valueChanges, awardingOrganization.valueChanges).pipe(
      debounceTime(SUGGEST_DEBOUNCE),
      map(() => ({
        title: [titleUk.value, title.value]
          .map((value) => value?.trim().substring(0, SUGGEST_TEXT_LIMIT) ?? '')
          .filter((value) => value !== '')
          .join(' '),
        organization: awardingOrganization.value?.trim().substring(0, SUGGEST_TEXT_LIMIT) ?? '',
      })),
      distinctUntilChanged((a, b) => a.title === b.title && a.organization === b.organization),
    );
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
        this.returned.set(
          award.request?.status === 'RETURNED' ? { comment: award.request.returnComment ?? null } : null,
        );
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
    const request =
      this.id === null
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
      recipient: award.recipient.type,
      recipientOrganizationId: award.recipient.organization?.id ?? null,
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
    if (this.id !== null && problemStatus(error) === HttpStatusCode.NotFound) {
      this.deletedElsewhere();
      return;
    }
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

  /** The draft was deleted in another window: the typed values become a new draft that is not saved yet. */
  private deletedElsewhere(): void {
    this.dropCopy();
    this.id = null;
    this.version = 0;
    this.current.set(null);
    this.stale.set(false);
    this.message.set(null);
    this.location.replaceState('/awards/new');
    this.form.markAsDirty();
    this.keepCopy();
    this.problem.set('awards.problems.award-deleted');
  }

  private problemKey(error: unknown): string {
    const type = problemType(error);
    return `awards.problems.${KNOWN_PROBLEMS.includes(type) ? type : 'unknown'}`;
  }

  private clearMarkedErrors(): void {
    this.problem.set(null);
    for (const control of Object.values(this.form.controls)) {
      if (control.hasError('server') || control.hasError('required')) {
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
      recipientOrganizationId:
        raw.recipient === 'UNIT' ? (raw.recipientOrganizationId ?? null) : null,
    };
  }

  private offerCopy(): void {
    const userId = this.copyOwner();
    const copy = userId ? this.copies.load<AwardForm>(userId, this.copyName()) : null;
    const differs = copy && JSON.stringify(copy) !== JSON.stringify(this.value());
    this.restoreOffer.set(differs ? copy : null);
  }

  private keepCopy(evenIfPristine = false): void {
    const userId = this.copyOwner();
    if (userId && (this.form.dirty || evenIfPristine) && !this.leaving) {
      this.copies.save(userId, this.copyName(), this.value());
    }
  }

  private dropCopy(): void {
    const userId = this.copyOwner();
    if (userId) {
      this.copies.remove(userId, this.copyName());
    }
  }

  /** The user the form was opened for; the copy stays theirs while the session is being renewed or has ended. */
  private copyOwner(): string | null {
    this.owner ??= this.auth.userId();
    return this.owner;
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
