import { createActionGroup, emptyProps, props } from '@ngrx/store';

import { ReadProblem } from '../../../core/api/problem';
import { Page } from '../../../shared/page';
import {
  ErasureEntry,
  ErasureStatus,
  HoldEntry,
  PageRequest,
  RetentionPolicy,
  RetentionRun,
} from '../compliance.service';

/** A section of the compliance page with a count in the side column. */
export type ComplianceSection = 'retention' | 'erasures' | 'holds';

export const ComplianceActions = createActionGroup({
  source: 'Compliance',
  events: {
    'Overview Opened': emptyProps(),
    'Count Loaded': props<{ section: ComplianceSection; count: number }>(),
    'Count Load Failed': props<{ section: ComplianceSection }>(),
    'Recent Runs Loaded': props<{ runs: RetentionRun[] }>(),
    'Recent Runs Load Failed': emptyProps(),
    'Retention Opened': props<{ request: PageRequest }>(),
    'Policies Loaded': props<{ policies: RetentionPolicy[] }>(),
    'Policies Load Failed': props<{ problem: ReadProblem }>(),
    'Runs Loaded': props<{ page: Page<RetentionRun> }>(),
    'Runs Load Failed': props<{ problem: ReadProblem }>(),
    'Erasures Opened': props<{ request: PageRequest; status: ErasureStatus | null }>(),
    'Erasures Loaded': props<{ page: Page<ErasureEntry> }>(),
    'Erasures Load Failed': props<{ problem: ReadProblem }>(),
    'Holds Opened': props<{ request: PageRequest; active: boolean }>(),
    'Holds Loaded': props<{ page: Page<HoldEntry> }>(),
    'Holds Load Failed': props<{ problem: ReadProblem }>(),
  },
});
