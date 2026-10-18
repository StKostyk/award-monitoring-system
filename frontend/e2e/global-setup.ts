import { NOTICE_VERSION, sql } from './helpers';

/**
 * Records the acknowledgement of the current privacy notice for every account whose latest acknowledgement is of
 * another version (or missing), so that seed accounts open the application instead of the acknowledgement page,
 * also after a manual run with another notice version; the redirect itself is covered by
 * `privacy-notice.spec.ts` with an account of its own.
 */
export default function globalSetup(): void {
  sql(
    `insert into consent_records (user_id, consent_type, consent_version, is_granted, granted_at)
     select u.user_id, 'DATA_PROCESSING', '${NOTICE_VERSION}', true, now() from users u
     where coalesce((select c.consent_version from consent_records c where c.user_id = u.user_id
       and c.consent_type = 'DATA_PROCESSING' order by c.created_at desc, c.consent_id desc
       limit 1), '') <> '${NOTICE_VERSION}'`,
  );
}
