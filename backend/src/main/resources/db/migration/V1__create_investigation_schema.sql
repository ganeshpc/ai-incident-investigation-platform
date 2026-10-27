-- Initial system-of-record schema.
-- Flyway runs this once on a clean database. Hibernate validates the mapped
-- tables against these columns and does not create them itself.

CREATE TABLE workload_registry (
    workload_id VARCHAR(200) PRIMARY KEY,
    display_name VARCHAR(300) NOT NULL,
    environment VARCHAR(100) NOT NULL,
    provenance VARCHAR(50) NOT NULL,
    autosys_job_name VARCHAR(200),
    definition JSONB NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE incidents (
    id UUID PRIMARY KEY,
    title VARCHAR(500) NOT NULL,
    workload_id VARCHAR(200) NOT NULL REFERENCES workload_registry (workload_id),
    environment VARCHAR(100) NOT NULL,
    incident_type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    failure_time TIMESTAMPTZ NOT NULL,
    lookback_minutes INTEGER NOT NULL,
    autosys_job_name VARCHAR(200),
    job_run_id VARCHAR(200),
    error_summary TEXT,
    investigation_attempt_count INTEGER NOT NULL,
    idempotency_key VARCHAR(200),
    request_hash VARCHAR(64),
    created_by VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT incidents_lookback_check CHECK (lookback_minutes BETWEEN 1 AND 10080),
    CONSTRAINT incidents_type_check CHECK (
        incident_type IN ('BATCH_FAILURE', 'WORKFLOW_FAILURE', 'UNKNOWN')
    ),
    CONSTRAINT incidents_status_check CHECK (
        status IN (
            'CREATED',
            'COLLECTING_EVIDENCE',
            'EVIDENCE_READY',
            'INVESTIGATING',
            'REPORT_READY',
            'PARTIAL',
            'FAILED'
        )
    ),
    CONSTRAINT incidents_idempotency_key_unique UNIQUE (idempotency_key)
);

CREATE INDEX incidents_workload_idx ON incidents (workload_id);
CREATE INDEX incidents_failure_time_idx ON incidents (failure_time);
CREATE INDEX incidents_created_at_idx ON incidents (created_at DESC);

CREATE TABLE investigation_attempts (
    id UUID PRIMARY KEY,
    incident_id UUID NOT NULL REFERENCES incidents (id),
    attempt_number INTEGER NOT NULL,
    stage VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    failure_reason TEXT,
    retry_of_attempt_id UUID REFERENCES investigation_attempts (id),
    rerun_successful_collections BOOLEAN NOT NULL DEFAULT FALSE,
    model_name VARCHAR(200),
    prompt_version VARCHAR(100),
    lease_owner VARCHAR(200),
    lease_until TIMESTAMPTZ,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT investigation_attempts_number_unique UNIQUE (incident_id, attempt_number),
    CONSTRAINT investigation_attempts_status_check CHECK (
        status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED')
    ),
    CONSTRAINT investigation_attempts_stage_check CHECK (
        stage IN (
            'CREATED',
            'COLLECTING_EVIDENCE',
            'EVIDENCE_READY',
            'INVESTIGATING',
            'REPORT_READY'
        )
    )
);

CREATE INDEX investigation_attempts_incident_idx ON investigation_attempts (incident_id);
CREATE INDEX investigation_attempts_claim_idx
    ON investigation_attempts (status, lease_until, created_at);

CREATE TABLE evidence_records (
    id UUID PRIMARY KEY,
    incident_id UUID NOT NULL REFERENCES incidents (id),
    attempt_id UUID NOT NULL REFERENCES investigation_attempts (id),
    evidence_key VARCHAR(200) NOT NULL,
    source_system VARCHAR(50) NOT NULL,
    workload_id VARCHAR(200) NOT NULL,
    evidence_type VARCHAR(50) NOT NULL,
    event_time TIMESTAMPTZ,
    event_time_precision VARCHAR(20) NOT NULL,
    retrieval_time TIMESTAMPTZ NOT NULL,
    source_reference TEXT NOT NULL,
    attributes JSONB NOT NULL DEFAULT '{}'::jsonb,
    content_excerpt TEXT,
    content_hash VARCHAR(64),
    provenance VARCHAR(50) NOT NULL,
    redaction_status VARCHAR(50) NOT NULL,
    role VARCHAR(50),
    CONSTRAINT evidence_records_key_unique UNIQUE (incident_id, evidence_key)
);

CREATE INDEX evidence_records_incident_idx ON evidence_records (incident_id);
CREATE INDEX evidence_records_incident_time_idx ON evidence_records (incident_id, event_time);

CREATE TABLE collection_results (
    id UUID PRIMARY KEY,
    incident_id UUID NOT NULL REFERENCES incidents (id),
    attempt_id UUID NOT NULL REFERENCES investigation_attempts (id),
    source_system VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ NOT NULL,
    warning TEXT,
    error_category VARCHAR(100),
    retry_appropriate BOOLEAN NOT NULL,
    diagnostic_message TEXT NOT NULL,
    CONSTRAINT collection_results_attempt_source_unique UNIQUE (attempt_id, source_system),
    CONSTRAINT collection_results_status_check CHECK (
        status IN ('SUCCESS', 'PARTIAL', 'UNAVAILABLE', 'UNCONFIGURED', 'FAILED')
    )
);

CREATE INDEX collection_results_incident_idx ON collection_results (incident_id);

CREATE TABLE hypotheses (
    id UUID PRIMARY KEY,
    incident_id UUID NOT NULL REFERENCES incidents (id),
    attempt_id UUID NOT NULL REFERENCES investigation_attempts (id),
    summary TEXT NOT NULL,
    category VARCHAR(100) NOT NULL,
    missing_evidence TEXT,
    confidence VARCHAR(20) NOT NULL,
    explanation TEXT NOT NULL,
    recommended_next_check TEXT,
    validation_status VARCHAR(50) NOT NULL,
    CONSTRAINT hypotheses_confidence_check CHECK (confidence IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT hypotheses_validation_check CHECK (
        validation_status IN (
            'SUPPORTED',
            'CONTRADICTED',
            'INCONCLUSIVE',
            'NEEDS_MORE_EVIDENCE'
        )
    )
);

CREATE INDEX hypotheses_incident_idx ON hypotheses (incident_id);

CREATE TABLE hypothesis_evidence (
    hypothesis_id UUID NOT NULL REFERENCES hypotheses (id),
    evidence_id UUID NOT NULL REFERENCES evidence_records (id),
    relationship VARCHAR(30) NOT NULL,
    PRIMARY KEY (hypothesis_id, evidence_id, relationship),
    CONSTRAINT hypothesis_evidence_relationship_check CHECK (
        relationship IN ('SUPPORTING', 'CONTRADICTING')
    )
);

CREATE TABLE investigation_reports (
    id UUID PRIMARY KEY,
    incident_id UUID NOT NULL REFERENCES incidents (id),
    attempt_id UUID NOT NULL UNIQUE REFERENCES investigation_attempts (id),
    status VARCHAR(50) NOT NULL,
    observed_failure TEXT,
    summary TEXT,
    missing_evidence TEXT,
    recommended_next_checks TEXT,
    limitations TEXT,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX investigation_reports_incident_idx ON investigation_reports (incident_id);

CREATE TABLE audit_events (
    id UUID PRIMARY KEY,
    incident_id UUID REFERENCES incidents (id),
    attempt_id UUID REFERENCES investigation_attempts (id),
    operation VARCHAR(80) NOT NULL,
    result VARCHAR(50) NOT NULL,
    message TEXT,
    occurred_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX audit_events_incident_time_idx ON audit_events (incident_id, occurred_at);
