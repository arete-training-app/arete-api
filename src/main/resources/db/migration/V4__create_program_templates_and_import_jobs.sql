-- Program templates: ownerless blueprints made of days at a relative position, instantiated into users' programs.
-- No column records where a template came from, on purpose.
CREATE TABLE program_template (
    id            BIGSERIAL     PRIMARY KEY,
    name          VARCHAR(100)  NOT NULL,
    description   VARCHAR(1000),
    content_hash  VARCHAR(64)   NOT NULL,
    day_count     INTEGER       NOT NULL,
    last_position INTEGER       NOT NULL,
    created_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP,
    version       BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT uq_program_template_content_hash UNIQUE (content_hash),
    CONSTRAINT chk_program_template_day_count CHECK (day_count > 0),
    CONSTRAINT chk_program_template_last_position CHECK (last_position >= day_count)
);

-- One row per covered day; a position without a row is an uncovered day
CREATE TABLE program_template_day (
    id                  BIGSERIAL  PRIMARY KEY,
    program_template_id BIGINT     NOT NULL,
    day_position        INTEGER    NOT NULL,
    created_at          TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP,
    version             BIGINT     NOT NULL DEFAULT 0,

    CONSTRAINT fk_program_template_day_program_template FOREIGN KEY (program_template_id) REFERENCES program_template(id) ON DELETE CASCADE,
    CONSTRAINT uq_program_template_day_template_position UNIQUE (program_template_id, day_position),
    CONSTRAINT chk_program_template_day_position CHECK (day_position >= 1)
);

CREATE TABLE program_template_part (
    id                      BIGSERIAL     PRIMARY KEY,
    program_template_day_id BIGINT        NOT NULL,
    order_index             INTEGER       NOT NULL,
    title                   VARCHAR(150)  NOT NULL,
    description             VARCHAR(4000),
    duration_minutes        INTEGER,

    CONSTRAINT fk_program_template_part_program_template_day FOREIGN KEY (program_template_day_id) REFERENCES program_template_day(id) ON DELETE CASCADE,
    -- Deferred like uq_workout_part_day_order, so a day's parts can be replaced in one flush
    CONSTRAINT uq_program_template_part_day_order UNIQUE (program_template_day_id, order_index) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT chk_program_template_part_order_index CHECK (order_index >= 0),
    CONSTRAINT chk_program_template_part_duration_minutes CHECK (duration_minutes > 0)
);

CREATE INDEX idx_program_template_part_program_template_day_id ON program_template_part(program_template_day_id);

-- Queue of imports processed in the background; rows are kept as history. A job only names the catalog entry (the
-- catalog folder) it imports; the handler reads the folder's files when it runs.
CREATE TABLE import_job (
    id              BIGSERIAL     PRIMARY KEY,
    type            VARCHAR(255)  NOT NULL,
    status          VARCHAR(255)  NOT NULL,
    catalog_entry   VARCHAR(100)  NOT NULL,
    attempts        INTEGER       NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP     NOT NULL,
    locked_at       TIMESTAMP,
    completed_at    TIMESTAMP,
    result_id       BIGINT,
    error           VARCHAR(2000),
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP,
    version         BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT chk_import_job_attempts CHECK (attempts >= 0)
);

CREATE INDEX idx_import_job_pending_next_attempt_at ON import_job(next_attempt_at) WHERE status = 'PENDING';
CREATE INDEX idx_import_job_processing_locked_at ON import_job(locked_at) WHERE status = 'PROCESSING';
-- The same catalog entry can't be queued twice while an earlier job for it is still active
CREATE UNIQUE INDEX uq_import_job_active_catalog_entry ON import_job(type, catalog_entry) WHERE status IN ('PENDING', 'PROCESSING');
-- Finds the latest job of a catalog entry, for the catalog listing
CREATE INDEX idx_import_job_type_catalog_entry ON import_job(type, catalog_entry);
