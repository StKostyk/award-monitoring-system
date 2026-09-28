import { HttpStatusCode } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatChip } from '@angular/material/chips';
import { MatProgressBar } from '@angular/material/progress-bar';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

import { problemStatus } from '../../../core/api/problem';
import { AuthService } from '../../../core/auth/auth.service';
import { LanguageService } from '../../../core/i18n/language.service';
import { organizationName } from '../../admin/role-organizations';
import { Award, AwardsService, awardTitle, categoryName } from '../awards.service';

@Component({
  selector: 'app-award-detail',
  imports: [RouterLink, MatButton, MatChip, MatProgressBar, TranslocoPipe],
  templateUrl: './award-detail.component.html',
  styleUrl: './award-detail.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AwardDetailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly service = inject(AwardsService);
  private readonly auth = inject(AuthService);
  private readonly language = inject(LanguageService);

  readonly award = signal<Award | null>(null);
  readonly loading = signal(false);
  readonly notFound = signal(false);
  readonly failed = signal(false);
  readonly notice = signal<string | null>(null);

  ngOnInit(): void {
    const problem = (history.state as { problem?: string } | null)?.problem;
    if (problem) {
      this.notice.set(`awards.problems.${problem}`);
    }
    const param = this.route.snapshot.paramMap.get('id') ?? '';
    if (!/^\d+$/.test(param)) {
      this.notFound.set(true);
      return;
    }
    this.loading.set(true);
    this.service.get(Number(param)).subscribe({
      next: (award) => {
        this.award.set(award);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.loading.set(false);
        if (problemStatus(error) === HttpStatusCode.NotFound) {
          this.notFound.set(true);
        } else {
          this.failed.set(true);
        }
      },
    });
  }

  editable(award: Award): boolean {
    return award.status === 'DRAFT' && String(award.owner.id) === this.auth.userId();
  }

  title(award: Award): string {
    return awardTitle(award, this.language.current());
  }

  category(award: Award): string {
    return award.category ? categoryName(award.category, this.language.current()) : '';
  }

  organization(award: Award): string {
    return organizationName(award.organization, this.language.current());
  }
}
