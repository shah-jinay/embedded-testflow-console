-- Firmware builds are immutable once created; no updated_at.
CREATE TABLE firmware_build (
    id                   UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    version              VARCHAR(100) NOT NULL,
    branch               VARCHAR(200),
    commit_hash          VARCHAR(40),
    is_release_candidate BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_firmware_build_version UNIQUE (version)
);

CREATE TABLE device (
    id                        UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    external_device_id        VARCHAR(200) NOT NULL,
    board_revision            VARCHAR(50),
    mcu_family                VARCHAR(100),
    current_firmware_build_id UUID         REFERENCES firmware_build(id) ON DELETE SET NULL,
    environment               VARCHAR(100),
    status                    VARCHAR(50)  NOT NULL DEFAULT 'ACTIVE',
    last_seen_at              TIMESTAMPTZ,
    metadata                  JSONB        NOT NULL DEFAULT '{}',
    created_at                TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at                TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_device_external_id UNIQUE (external_device_id)
);

CREATE TABLE test_suite (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name             VARCHAR(200) NOT NULL,
    target_component VARCHAR(200),
    owner            VARCHAR(200),
    severity         VARCHAR(50),
    description      TEXT,
    CONSTRAINT uq_test_suite_name UNIQUE (name)
);

CREATE TABLE test_case (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    suite_id          UUID         NOT NULL REFERENCES test_suite(id) ON DELETE CASCADE,
    name              VARCHAR(200) NOT NULL,
    expected_behavior TEXT,
    timeout_ms        BIGINT,
    criticality       VARCHAR(50),
    CONSTRAINT uq_test_case_suite_name UNIQUE (suite_id, name)
);

CREATE TABLE test_run (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    external_run_id    VARCHAR(200) NOT NULL,
    device_id          UUID         NOT NULL REFERENCES device(id),
    firmware_build_id  UUID         NOT NULL REFERENCES firmware_build(id),
    suite_id           UUID         NOT NULL REFERENCES test_suite(id),
    status             VARCHAR(50)  NOT NULL DEFAULT 'QUEUED',
    environment        VARCHAR(100),
    correlation_id     VARCHAR(100),
    started_at         TIMESTAMPTZ,
    completed_at       TIMESTAMPTZ,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_test_run_identity UNIQUE (device_id, firmware_build_id, suite_id, external_run_id)
);

CREATE TABLE test_result (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id           UUID        NOT NULL REFERENCES test_run(id) ON DELETE CASCADE,
    case_id          UUID        NOT NULL REFERENCES test_case(id),
    attempt_number   INT         NOT NULL DEFAULT 1,
    status           VARCHAR(50) NOT NULL,
    duration_ms      BIGINT,
    failure_category VARCHAR(100),
    failure_message  TEXT,
    log_reference    VARCHAR(500),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_test_result_identity UNIQUE (run_id, case_id, attempt_number)
);

-- Indexes specified in §6
CREATE INDEX idx_test_run_firmware_status_started
    ON test_run (firmware_build_id, status, started_at DESC);

CREATE INDEX idx_test_run_device_started
    ON test_run (device_id, started_at DESC);

CREATE INDEX idx_test_run_suite_status
    ON test_run (suite_id, status);

CREATE INDEX idx_test_result_run
    ON test_result (run_id);

CREATE INDEX idx_test_result_failure_category
    ON test_result (failure_category, created_at DESC);
