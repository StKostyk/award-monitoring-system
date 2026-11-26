-- The owner may withdraw a request no reviewer has claimed yet; the award becomes a draft again and a later
-- submission reuses the request, so its earlier decisions stay on the timeline.

ALTER TABLE award_requests DROP CONSTRAINT ck_award_requests_status;
ALTER TABLE award_requests ADD CONSTRAINT ck_award_requests_status
    CHECK (status IN ('SUBMITTED', 'IN_REVIEW', 'ESCALATED', 'APPROVED', 'REJECTED', 'RETURNED', 'EXPIRED',
                      'WITHDRAWN'));

COMMENT ON COLUMN award_requests.status IS
    'SUBMITTED, IN_REVIEW, ESCALATED, APPROVED, REJECTED, RETURNED, EXPIRED (unused), WITHDRAWN (by the owner '
    'before a claim); RETURNED and WITHDRAWN go back to SUBMITTED on resubmission';
