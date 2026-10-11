-- V036__event_publication.sql
-- Description: Spring Modulith event publication registry for the after-commit listeners (schema of
--              spring-modulith-events-jdbc 1.4, schema-postgresql.sql)
-- Author: Stefan Kostyk
-- Date: 2026-10-09

-- ============================================================================
-- EVENT_PUBLICATION
-- ============================================================================

CREATE TABLE event_publication (
    id               UUID        NOT NULL,
    listener_id      TEXT        NOT NULL,
    event_type       TEXT        NOT NULL,
    serialized_event TEXT        NOT NULL,
    publication_date TIMESTAMPTZ NOT NULL,
    completion_date  TIMESTAMPTZ NULL,
    CONSTRAINT pk_event_publication PRIMARY KEY (id)
);

CREATE INDEX event_publication_serialized_event_hash_idx ON event_publication USING hash (serialized_event);
CREATE INDEX event_publication_by_completion_date_idx ON event_publication (completion_date);

COMMENT ON TABLE event_publication IS
    'Event publication registry: one row per after-commit listener of a committed event, deleted when the listener '
    'completes. Incomplete rows are retried between 10 minutes and 24 hours after publication and deleted after 30 '
    'days. serialized_event holds personal data (recipient address, first name, award title); one-time links are '
    'never stored';
COMMENT ON COLUMN event_publication.listener_id IS 'Listener method signature (class, method, parameter type)';
COMMENT ON COLUMN event_publication.event_type IS 'Fully qualified class name of the event';
COMMENT ON COLUMN event_publication.serialized_event IS 'The event as JSON';
COMMENT ON COLUMN event_publication.publication_date IS 'Time the event was published inside the business transaction';
COMMENT ON COLUMN event_publication.completion_date IS
    'Always NULL here: completed publications are deleted (completion mode delete)';
