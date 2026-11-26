import { HttpStatusCode } from '@angular/common/http';
import { Component, OnInit, inject, input, output, signal } from '@angular/core';
import { MatAnchor, MatButton } from '@angular/material/button';
import { MatCard, MatCardContent, MatCardHeader, MatCardTitle } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { Router, RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

import { knownProblem, problemStatus, problemType } from '../../../core/api/problem';
import { LanguageService } from '../../../core/i18n/language.service';
import { kyivDateTime } from '../../../shared/date-format';
import { Award, awardTitle } from '../../awards/awards.service';
import { confirmAction } from '../../awards/confirm-dialog/confirm-dialog.component';
import { PeopleService, Person, PersonDetail } from '../people.service';
import { PersonDialogComponent, PersonDialogData } from '../person-dialog/person-dialog.component';

/** Problems the card explains in its own words; anything else shows the generic message. */
const KNOWN = [
  'person-has-awards',
  'person-claimed',
  'no-address',
  'too-many-requests',
  'access-denied',
  'network',
];

@Component({
  selector: 'app-person-card',
  imports: [
    MatCard,
    MatCardHeader,
    MatCardTitle,
    MatCardContent,
    MatAnchor,
    MatButton,
    RouterLink,
    TranslocoPipe,
  ],
  templateUrl: './person-card.component.html',
  styleUrl: '../user-detail/user-detail.component.scss',
})
export class PersonCardComponent implements OnInit {
  private readonly api = inject(PeopleService);
  private readonly dialog = inject(MatDialog);
  private readonly router = inject(Router);
  private readonly language = inject(LanguageService);

  readonly personId = input.required<number>();
  /** Emits after a change, so the page reloads the account it shows. */
  readonly changed = output<void>();

  readonly person = signal<PersonDetail | null>(null);
  readonly busy = signal(false);
  readonly message = signal<string | null>(null);
  readonly problem = signal<string | null>(null);

  ngOnInit(): void {
    this.load();
  }

  change(person: Person): void {
    const data: PersonDialogData = { person };
    this.dialog
      .open(PersonDialogComponent, { data, width: '520px' })
      .afterClosed()
      .subscribe((saved?: Person) => {
        if (saved) {
          this.done('admin.people.changed');
          this.changed.emit();
        }
      });
  }

  invite(person: Person): void {
    this.busy.set(true);
    this.api.invite(person.id).subscribe({
      next: () => this.done('admin.people.invited'),
      error: (err: unknown) => this.refused(err),
    });
  }

  remove(person: Person): void {
    confirmAction(this.dialog, 'admin.people.remove', {
      name: `${person.firstName} ${person.lastName}`,
    }).subscribe((confirmed) => {
      if (confirmed) {
        this.busy.set(true);
        this.api.remove(person.id).subscribe({
          next: () => void this.router.navigate(['/admin/users']),
          error: (err: unknown) => this.refused(err),
        });
      }
    });
  }

  title(award: Award): string {
    return awardTitle(award, this.language.current());
  }

  moment(value: string): string {
    return kyivDateTime(value, this.language.current());
  }

  private load(): void {
    this.api.get(this.personId()).subscribe({
      next: (person) => {
        this.busy.set(false);
        this.person.set(person);
      },
      error: (err: unknown) => this.refused(err),
    });
  }

  private done(message: string): void {
    this.problem.set(null);
    this.message.set(message);
    this.load();
  }

  private refused(err: unknown): void {
    this.busy.set(false);
    this.message.set(null);
    const type =
      problemStatus(err) === HttpStatusCode.TooManyRequests
        ? 'too-many-requests'
        : problemType(err);
    this.problem.set(`admin.people.problems.${knownProblem(type, KNOWN)}`);
    if (type === 'person-claimed') {
      this.changed.emit();
    }
  }
}
