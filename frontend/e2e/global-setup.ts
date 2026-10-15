import { NOTICE_VERSION, sql } from './helpers';

/**
 * Records the acknowledgement of the current privacy notice for every account without one, so that seed accounts
 * open the application instead of the acknowledgement page; the redirect itself is covered by
 * `privacy-notice.spec.ts` with an account of its own.
 */
export default function globalSetup(): void {
  sql(
    `insert into consent_records (user_id, consent_type, consent_version, is_granted, granted_at)
     select u.user_id, 'DATA_PROCESSING', '${NOTICE_VERSION}', true, now() from users u
     where not exists (select 1 from consent_records c where c.user_id = u.user_id
       and c.consent_type = 'DATA_PROCESSING' and c.consent_version = '${NOTICE_VERSION}')`,
  );
}
