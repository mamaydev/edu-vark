-- 001_init.sql — EduVARK initial schema
-- Generated from docs/design/DATABASE_SCHEMA_DRAFT.sql (validated in disposable PostgreSQL).
-- The migration runner wraps this file in a transaction.


CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ------------------------------------------------------------
-- USERS
-- ------------------------------------------------------------
CREATE TABLE users (
    id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    login_id              text NOT NULL,
    password_hash         text NOT NULL,
    full_name             text NOT NULL,
    role                  text NOT NULL,
    status                text NOT NULL DEFAULT 'ACTIVE',
    must_change_password  boolean NOT NULL DEFAULT true,
    created_at            timestamptz NOT NULL DEFAULT now(),
    updated_at            timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ux_users_login UNIQUE (login_id),
    CONSTRAINT ck_users_login CHECK (char_length(btrim(login_id)) BETWEEN 1 AND 50),
    CONSTRAINT ck_users_name CHECK (char_length(btrim(full_name)) BETWEEN 1 AND 150),
    CONSTRAINT ck_users_role CHECK (role IN ('STUDENT', 'LECTURER', 'ADMIN')),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

-- ------------------------------------------------------------
-- CLASSES (entity COURSE_CLASS in the ERD)
-- ------------------------------------------------------------
CREATE TABLE classes (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code        text NOT NULL,
    name        text NOT NULL,
    semester    text NOT NULL,
    is_active   boolean NOT NULL DEFAULT true,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ux_classes_code UNIQUE (code),
    CONSTRAINT ck_classes_code CHECK (char_length(btrim(code)) BETWEEN 1 AND 30)
);

-- Only one active class in the MVP.
CREATE UNIQUE INDEX ux_classes_single_active
    ON classes (is_active)
    WHERE is_active;

-- ------------------------------------------------------------
-- ENROLLMENTS
-- ------------------------------------------------------------
CREATE TABLE enrollments (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    class_id     uuid NOT NULL REFERENCES classes(id) ON DELETE RESTRICT,
    student_id   uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    status       text NOT NULL DEFAULT 'ACTIVE',
    enrolled_at  timestamptz NOT NULL DEFAULT now(),
    left_at      timestamptz,
    CONSTRAINT ux_enrollment UNIQUE (class_id, student_id),
    CONSTRAINT ck_enrollment_status CHECK (status IN ('ACTIVE', 'LEFT')),
    CONSTRAINT ck_enrollment_left CHECK (
        (status = 'ACTIVE' AND left_at IS NULL)
        OR (status = 'LEFT' AND left_at IS NOT NULL)
    )
);

CREATE INDEX ix_enrollment_student ON enrollments (student_id, status);

-- ------------------------------------------------------------
-- TEACHING ASSIGNMENTS
-- ------------------------------------------------------------
CREATE TABLE teaching_assignments (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    class_id     uuid NOT NULL REFERENCES classes(id) ON DELETE RESTRICT,
    lecturer_id  uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    assigned_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ux_teaching UNIQUE (class_id, lecturer_id)
);

-- ------------------------------------------------------------
-- AUTH SESSIONS
-- ------------------------------------------------------------
CREATE TABLE auth_sessions (
    id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id              uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash           text NOT NULL,
    refresh_token_hash   text NOT NULL,
    vark_type            text,
    is_active            boolean NOT NULL DEFAULT true,
    created_at           timestamptz NOT NULL DEFAULT now(),
    last_seen_at         timestamptz NOT NULL DEFAULT now(),
    expires_at           timestamptz NOT NULL,
    revoked_at           timestamptz,
    user_agent           text,
    ip_hash              text,
    CONSTRAINT ux_auth_token UNIQUE (token_hash),
    CONSTRAINT ux_auth_refresh UNIQUE (refresh_token_hash),
    CONSTRAINT ck_auth_vark CHECK (
        vark_type IS NULL
        OR vark_type IN ('VISUAL', 'AURAL', 'READ_WRITE', 'KINESTHETIC')
    ),
    CONSTRAINT ck_auth_expiry CHECK (expires_at > created_at)
);

-- One active session per user. Expired/revoked sessions must set is_active=false
-- in the login/refresh transaction or a cleanup job.
CREATE UNIQUE INDEX ux_auth_active_user
    ON auth_sessions (user_id)
    WHERE is_active;

CREATE INDEX ix_auth_user_created ON auth_sessions (user_id, created_at DESC);

-- ------------------------------------------------------------
-- TOPICS
-- ------------------------------------------------------------
CREATE TABLE topics (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    class_id     uuid NOT NULL REFERENCES classes(id) ON DELETE RESTRICT,
    created_by   uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    title        text NOT NULL,
    description  text,
    sort_order   integer NOT NULL DEFAULT 0,
    status       text NOT NULL DEFAULT 'DRAFT',
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now(),
    deleted_at   timestamptz,
    CONSTRAINT ck_topics_title CHECK (char_length(btrim(title)) BETWEEN 1 AND 200),
    CONSTRAINT ck_topics_order CHECK (sort_order >= 0),
    CONSTRAINT ck_topics_status CHECK (status IN ('DRAFT', 'PUBLISHED'))
);

CREATE INDEX ix_topics_class_status ON topics (class_id, status, sort_order);

-- Reorder within a transaction; avoid duplicate live sort_order.
CREATE UNIQUE INDEX ux_topic_order
    ON topics (class_id, sort_order)
    WHERE deleted_at IS NULL;

-- ------------------------------------------------------------
-- MATERIALS
-- ------------------------------------------------------------
CREATE TABLE materials (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    topic_id     uuid NOT NULL REFERENCES topics(id) ON DELETE RESTRICT,
    uploaded_by  uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    vark_type    text NOT NULL,
    title        text NOT NULL,
    kind         text NOT NULL,
    object_key   text,
    url          text,
    mime         text,
    size_bytes   bigint,
    uploaded_at  timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now(),
    deleted_at   timestamptz,
    CONSTRAINT ck_material_vark CHECK (
        vark_type IN ('VISUAL', 'AURAL', 'READ_WRITE', 'KINESTHETIC')
    ),
    CONSTRAINT ck_material_title CHECK (char_length(btrim(title)) BETWEEN 1 AND 200),
    CONSTRAINT ck_material_kind CHECK (kind IN ('FILE', 'LINK')),
    CONSTRAINT ck_material_shape CHECK (
        (kind = 'FILE'
            AND object_key IS NOT NULL
            AND mime IS NOT NULL
            AND url IS NULL
            AND size_bytes BETWEEN 1 AND 104857600)
        OR
        (kind = 'LINK'
            AND url IS NOT NULL
            AND url ~ '^https://'
            AND object_key IS NULL
            AND size_bytes IS NULL)
    )
);

CREATE INDEX ix_material_topic_vark
    ON materials (topic_id, vark_type)
    WHERE deleted_at IS NULL;

-- ------------------------------------------------------------
-- FEEDBACK
-- ------------------------------------------------------------
CREATE TABLE feedback (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id   uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    topic_id     uuid NOT NULL REFERENCES topics(id) ON DELETE RESTRICT,
    vark_type    text NOT NULL,
    value        integer NOT NULL,
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ux_feedback_triple UNIQUE (student_id, topic_id, vark_type),
    CONSTRAINT ck_feedback_vark CHECK (vark_type IN ('VISUAL', 'AURAL', 'READ_WRITE')),
    CONSTRAINT ck_feedback_value CHECK (value BETWEEN 0 AND 100)
);

CREATE INDEX ix_feedback_topic ON feedback (topic_id, vark_type);

-- ------------------------------------------------------------
-- CASE STUDIES (1:1 with topic)
-- ------------------------------------------------------------
CREATE TABLE case_studies (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    topic_id      uuid NOT NULL REFERENCES topics(id) ON DELETE RESTRICT,
    created_by    uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    theory        text,
    instructions  text NOT NULL,
    deadline_at   timestamptz NOT NULL,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    deleted_at    timestamptz,
    CONSTRAINT ck_case_instructions CHECK (
        char_length(btrim(instructions)) BETWEEN 1 AND 10000
    )
);

CREATE UNIQUE INDEX ux_case_topic
    ON case_studies (topic_id)
    WHERE deleted_at IS NULL;

-- ------------------------------------------------------------
-- SUBMISSIONS
-- ------------------------------------------------------------
CREATE TABLE submissions (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    case_study_id  uuid NOT NULL REFERENCES case_studies(id) ON DELETE RESTRICT,
    student_id     uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    answer_text    text NOT NULL,
    submitted_at   timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ux_submission_pair UNIQUE (student_id, case_study_id),
    CONSTRAINT ck_submission_length CHECK (
        char_length(answer_text) BETWEEN 1 AND 10000
    )
);

CREATE INDEX ix_submission_case ON submissions (case_study_id);
CREATE INDEX ix_submission_student ON submissions (student_id, submitted_at DESC);

-- ------------------------------------------------------------
-- ASSESSMENTS
-- ------------------------------------------------------------
CREATE TABLE assessments (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    submission_id  uuid NOT NULL REFERENCES submissions(id) ON DELETE RESTRICT,
    assessed_by    uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    score          integer NOT NULL,
    comment        text,
    assessed_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ux_assessment_submission UNIQUE (submission_id),
    CONSTRAINT ck_assessment_score CHECK (score BETWEEN 0 AND 100),
    CONSTRAINT ck_assessment_comment CHECK (
        comment IS NULL OR char_length(comment) <= 1000
    )
);

-- ------------------------------------------------------------
-- AUDIT LOGS
-- ------------------------------------------------------------
CREATE TABLE audit_logs (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id    uuid REFERENCES users(id) ON DELETE SET NULL,
    action      text NOT NULL,
    entity      text NOT NULL,
    entity_id   uuid,
    metadata    jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at  timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX ix_audit_actor_time ON audit_logs (actor_id, created_at DESC);
CREATE INDEX ix_audit_entity ON audit_logs (entity, entity_id, created_at DESC);

-- ============================================================
-- TRIGGERS
-- ============================================================

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_users_updated
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_classes_updated
    BEFORE UPDATE ON classes
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_topics_updated
    BEFORE UPDATE ON topics
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_materials_updated
    BEFORE UPDATE ON materials
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_feedback_updated
    BEFORE UPDATE ON feedback
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_cases_updated
    BEFORE UPDATE ON case_studies
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- VARK on a session is write-once.
CREATE OR REPLACE FUNCTION enforce_session_vark_lock()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.vark_type IS NOT NULL
       AND NEW.vark_type IS DISTINCT FROM OLD.vark_type THEN
        RAISE EXCEPTION 'VARK_LOCKED';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_session_vark_lock
    BEFORE UPDATE ON auth_sessions
    FOR EACH ROW EXECUTE FUNCTION enforce_session_vark_lock();

-- Submission text is immutable.
CREATE OR REPLACE FUNCTION enforce_submission_immutable()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'SUBMISSION_IMMUTABLE';
    END IF;
    IF NEW.answer_text IS DISTINCT FROM OLD.answer_text
       OR NEW.case_study_id IS DISTINCT FROM OLD.case_study_id
       OR NEW.student_id IS DISTINCT FROM OLD.student_id THEN
        RAISE EXCEPTION 'SUBMISSION_IMMUTABLE';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_submission_immutable
    BEFORE UPDATE OR DELETE ON submissions
    FOR EACH ROW EXECUTE FUNCTION enforce_submission_immutable();

-- Assessment is insert-only.
CREATE OR REPLACE FUNCTION enforce_assessment_immutable()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'ASSESSMENT_IMMUTABLE';
END;
$$;

CREATE TRIGGER trg_assessment_immutable
    BEFORE UPDATE OR DELETE ON assessments
    FOR EACH ROW EXECUTE FUNCTION enforce_assessment_immutable();

-- Audit log is append-only.
CREATE OR REPLACE FUNCTION enforce_audit_append_only()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'AUDIT_APPEND_ONLY';
END;
$$;

CREATE TRIGGER trg_audit_append_only
    BEFORE UPDATE OR DELETE ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION enforce_audit_append_only();

-- Deadline cannot change after the first submission.
CREATE OR REPLACE FUNCTION enforce_deadline_lock()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.deadline_at IS DISTINCT FROM OLD.deadline_at THEN
        IF EXISTS (
            SELECT 1 FROM submissions WHERE case_study_id = OLD.id
        ) THEN
            RAISE EXCEPTION 'DEADLINE_LOCKED';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_deadline_lock
    BEFORE UPDATE ON case_studies
    FOR EACH ROW EXECUTE FUNCTION enforce_deadline_lock();

-- Hard delete of topics with academic data is forbidden; use soft delete.
CREATE OR REPLACE FUNCTION prevent_topic_hard_delete()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM case_studies cs
        JOIN submissions s ON s.case_study_id = cs.id
        WHERE cs.topic_id = OLD.id
    ) THEN
        RAISE EXCEPTION 'TOPIC_HAS_ACADEMIC_DATA';
    END IF;
    RETURN OLD;
END;
$$;

CREATE TRIGGER trg_prevent_topic_hard_delete
    BEFORE DELETE ON topics
    FOR EACH ROW EXECUTE FUNCTION prevent_topic_hard_delete();

