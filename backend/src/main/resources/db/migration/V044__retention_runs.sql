-- ============================================================================
-- V044: Runs of the nightly retention job (Feature 6.2, story 6.2.1)
-- ============================================================================
-- One row per run with the counts of every policy. A run left RUNNING by a
-- stop is marked INTERRUPTED by the next run. Rows hold counts and policy keys
-- only and are kept without a period.
-- ============================================================================

CREATE TABLE retention_runs (
    run_id BIGSERIAL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    finished_at TIMESTAMP WITH TIME ZONE,
    status VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
    results JSONB NOT NULL DEFAULT '[]',
    CONSTRAINT pk_retention_runs PRIMARY KEY (run_id),
    CONSTRAINT ck_retention_runs_status CHECK (status IN ('RUNNING', 'COMPLETED', 'PARTIAL', 'INTERRUPTED')),
    CONSTRAINT ck_retention_runs_finished CHECK ((status = 'RUNNING') = (finished_at IS NULL))
);

CREATE INDEX idx_retention_runs_started ON retention_runs(started_at DESC);

COMMENT ON TABLE retention_runs IS 'Runs of the nightly retention job with the counts of each policy';
COMMENT ON COLUMN retention_runs.run_id IS 'Unique run identifier';
COMMENT ON COLUMN retention_runs.started_at IS 'When the run started';
COMMENT ON COLUMN retention_runs.finished_at IS 'When the run ended or was found interrupted; null while RUNNING';
COMMENT ON COLUMN retention_runs.status IS 'RUNNING, COMPLETED, PARTIAL (a policy failed) or INTERRUPTED';
COMMENT ON COLUMN retention_runs.results IS
    'Per policy {policy, affected, held, failed, error}; error is the exception class only';
