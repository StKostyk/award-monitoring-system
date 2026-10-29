import { Location } from '@angular/common';
import { HttpErrorResponse, HttpStatusCode } from '@angular/common/http';
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

import {
  ReadProblem,
  fieldProblems,
  knownProblem,
  problemStatus,
  problemType,
  readProblem,
} from '../../../core/api/problem';
import { AuthService } from '../../../core/auth/auth.service';
import { ConnectivityService } from '../../../core/connectivity/connectivity.service';
import { LanguageService } from '../../../core/i18n/language.service';
import { FormCopiesService } from '../../../core/storage/form-copies.service';
import { kyivToday, yearsBefore } from '../../../shared/date-format';
import { organizationName } from '../../../shared/organization-name';
import { coarsePointer } from '../../../shared/touch';
import { TranslatedDatepickerIntl } from '../../../shared/translated-datepicker-intl';
import { AwardDocumentsComponent } from '../award-documents/award-documents.component';
import {
  DESCRIPTION_LIMIT,
  ORGANIZATION_LIMIT,
  TITLE_LIMIT,
  URL_LIMIT,
  WEB_LINK,
  empty,
  fieldErrorKey,
  text,
  titleInOneLanguage,
} from '../award-fields';
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
/** Stored key of a new award saved without a connection, shared by every tab of the user. */
const NEW_KEY = 'award-new-key';
const SUGGEST_DEBOUNCE = 400;
/** Characters of each text sent for suggestions; enough for the rules and short enough for a request line. */
const SUGGEST_TEXT_LIMIT = 300;
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
  providers: [{ provide: MatDatepickerIntl, useClass: TranslatedDatepickerIntl }],
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
  private readonly connectivity = inject(ConnectivityService);

  private id: number | null = null;
  private owner: string | null = null;
  private creationKey: string | null = null;
  private version = 0;
  private leaving = false;
  private running: Observable<Award> | null = null;
  private changedOffline = false;

  /** The date picker opens as a full-screen dialog on touch screens. */
  readonly touch = coarsePointer();
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
  /** Why the draft of this address could not be loaded; the form stays hidden until it is. */
  readonly loadFailed = signal<ReadProblem | 'network' | null>(null);
  readonly message = signal<string | null>(null);
  readonly problem = signal<string | null>(null);
  readonly stale = signal(false);
  readonly restoreOffer = signal<AwardForm | null>(null);
  readonly suggestions = signal<CategorySuggestion[]>([]);
  readonly categoryChosen = signal(false);
  readonly documentCount = signal(0);
  readonly units = signal<UnitRef[]>([]);
  readonly unitChosen = signal(false);
  readonly offline = computed(() => !this.connectivity.online());
  /** A save was asked for, or failed for want of a connection, and runs once the connection returns. */
  readonly pending = signal(false);
  readonly titleNeeded = signal(false);
  readonly notStored = signal(false);
  readonly submitRefused = signal(false);
  readonly offlineNotice = computed(() => {
    if (this.titleNeeded()) {
      return 'awards.offline.titleNeeded';
    }
    if (!this.offline()) {
      return null;
    }
    if (this.notStored()) {
      return 'awards.offline.notStored';
    }
    return this.pending() ? 'awards.offline.pending' : 'awards.offline.notice';
  });
  /** The recipient choice is offered to faculty secretaries and deans, and kept on a unit draft of anyone. */
  readonly unitOptions = computed(() => {
    const current = this.current()?.recipient.organization;
    return current && !this.units().some((unit) => unit.id === current.id)
      ? [current, ...this.units()]
      : this.units();
  });
  /** A unit no longer offered was restored or refused; the choice stays so that the award can go to «Я». */
  readonly recipientKept = signal(false);
  readonly recipientShown = computed(() => this.unitOptions().length > 0 || this.recipientKept());
  private readonly chosenUnit = signal<number | null>(null);
  /** The chosen unit when the person can no longer choose it, so the picker names it as such. */
  readonly unavailableUnit = computed(() => {
    const id = this.chosenUnit();
    return id !== null && !this.unitOptions().some((unit) => unit.id === id) ? id : null;
  });
  readonly editing = computed(() => this.current() !== null);
  readonly removable = computed(() => {
    const award = this.current();
    return award !== null && award.request === null;
  });
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
      externalUrl: ['', [Validators.maxLength(URL_LIMIT), Validators.pattern(WEB_LINK)]],
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
      .pipe(
        tap(() => (this.changedOffline ||= this.offline())),
        debounceTime(COPY_DEBOUNCE),
        takeUntilDestroyed(),
      )
      .subscribe(() => this.keepCopy());
    this.connectivity.reconnected$.pipe(takeUntilDestroyed()).subscribe(() => this.reconnected());
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
    this.form.controls.recipientOrganizationId.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe((id) => this.chosenUnit.set(id ?? null));
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
          this.leaving = true;
        }
      }),
    );
  }

  restore(): void {
    const copy = this.restoreOffer();
    const userId = this.copyOwner();
    if (this.id === null && userId) {
      this.creationKey ??= this.copies.load<string>(userId, NEW_KEY);
    }
    if (copy) {
      this.recipientKept.update((kept) => kept || copy.recipientOrganizationId !== null);
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

  /** Loads the draft of this address again after its load failed. */
  retry(): void {
    if (this.id !== null && !this.loading()) {
      this.load(this.id);
    }
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
    this.titleNeeded.set(false);
    if (this.form.invalid || this.unitMissing()) {
      this.form.markAllAsTouched();
      return;
    }
    if (!this.connectivity.linked()) {
      this.goPending(false);
      return;
    }
    this.persist('awards.messages.saved');
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
    if (!this.connectivity.linked()) {
      this.submitRefused.set(true);
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

  private persist(message: string): void {
    this.start();
    const request = this.store().pipe(
      finalize(() => (this.running = null)),
      share(),
    );
    this.running = request;
    request.subscribe({
      next: (award) => this.finish(award, message),
      error: (error: unknown) => this.failed(error),
    });
  }

  /** Saves what was entered or asked for while offline, once, when the connection returns. */
  private reconnected(): void {
    this.submitRefused.set(false);
    if (this.saving() || this.leaving) {
      return;
    }
    if (this.loading()) {
      return;
    }
    if (this.loadFailed() !== null) {
      this.retry();
      return;
    }
    const due = this.pending() || (this.changedOffline && this.form.dirty);
    this.changedOffline = false;
    if (!due) {
      return;
    }
    if (this.form.invalid || this.unitMissing()) {
      this.pending.set(false);
      this.titleNeeded.set(this.form.hasError('titleRequired'));
      this.form.markAllAsTouched();
      return;
    }
    this.persist('awards.messages.reconnected');
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
    if (id === null || !this.removable() || this.saving()) {
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
    return fieldErrorKey(this.form.controls[field].errors);
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
        this.loadFailed.set(null);
        this.show(award);
        this.returned.set(
          award.request?.status === 'RETURNED'
            ? { comment: award.request.returnComment ?? null }
            : null,
        );
        this.stale.set(false);
        this.problem.set(null);
        this.offerCopy();
      },
      error: (error: unknown) => {
        this.loading.set(false);
        if (problemStatus(error) === HttpStatusCode.NotFound) {
          this.notFound.set(true);
        } else if (this.current() !== null) {
          this.problem.set(this.problemKey(error));
        } else {
          this.loadFailed.set(problemType(error) === 'network' ? 'network' : readProblem(error));
        }
      },
    });
  }

  private store(): Observable<Award> {
    const value = this.value();
    if (this.id !== null) {
      return this.service
        .update(this.id, value, this.version)
        .pipe(tap((award) => this.show(award)));
    }
    this.creationKey ??= crypto.randomUUID();
    return this.service.create(value, this.creationKey).pipe(
      tap(({ award, repeated }) => {
        this.dropCopy();
        this.id = award.id;
        this.creationKey = null;
        this.location.replaceState(`/awards/${award.id}/edit`);
        this.show(award);
        if (repeated && JSON.stringify(this.value()) !== JSON.stringify(value)) {
          this.restoreOffer.set(value);
        }
      }),
      map(({ award }) => award),
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
    this.pending.set(false);
    this.titleNeeded.set(false);
    this.submitRefused.set(false);
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
    if (error instanceof HttpErrorResponse && error.status === 0) {
      this.connectivity.unreachable();
      this.goPending(true);
      return;
    }
    const known = this.id !== null || this.creationKey !== null;
    if (known && problemStatus(error) === HttpStatusCode.NotFound) {
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
      if (problem.field === 'recipientOrganizationId') {
        this.recipientKept.set(true);
      }
    }
    this.problem.set(this.problemKey(error));
  }

  private goPending(evenIfPristine: boolean): void {
    this.message.set(null);
    this.pending.set(true);
    this.keepCopy(evenIfPristine);
  }

  /** The draft was deleted in another window: the typed values become a new draft that is not saved yet. */
  private deletedElsewhere(): void {
    this.dropCopy();
    this.id = null;
    this.creationKey = null;
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
    const fields = fieldProblems(error);
    const onlyRecipient =
      fields.length === 1 &&
      fields[0].field === 'recipientOrganizationId' &&
      fields[0].code === 'out-of-scope';
    const type = onlyRecipient ? 'recipient-out-of-scope' : problemType(error);
    return `awards.problems.${knownProblem(type, KNOWN_PROBLEMS)}`;
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
      this.notStored.set(!this.copies.save(userId, this.copyName(), this.value()));
      if (this.id === null && this.offline()) {
        this.creationKey ??= this.copies.load<string>(userId, NEW_KEY) ?? crypto.randomUUID();
        this.copies.save(userId, NEW_KEY, this.creationKey);
      }
    }
  }

  private dropCopy(): void {
    const userId = this.copyOwner();
    if (!userId) {
      return;
    }
    this.copies.remove(userId, this.copyName());
    if (this.id === null && this.creationKey !== null) {
      if (this.copies.load<string>(userId, NEW_KEY) === this.creationKey) {
        this.copies.remove(userId, NEW_KEY);
      }
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
