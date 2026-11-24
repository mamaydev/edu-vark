-- 002_admin_login_ids.sql — Admin identity migration (FR-001/034/036, DEC-006)
--
-- Every Admin account moves to the system-issued pattern `014014` followed by
-- a five digit sequence (01401400001–01401499999). The backfill:
--   * is deterministic (created_at, then id),
--   * skips any identifier already used by ANY role (global uniqueness),
--   * preserves the account UUID, password, status, must_change_password and
--     every relational reference (sessions, audit logs, enrollments, etc.).
-- The legacy `admin` username is retired by the application (login rejects
-- identifiers outside the role pattern); no usernames are deleted here.
-- Migration 001 stays untouched.
--
-- SESSION REVOCATION IS ATOMIC WITH THE IDENTITY CHANGE. This migration keeps
-- a migration-specific marker for every account it re-identifies and revokes
-- that account's active sessions in the same transaction. It never uses
-- `users.updated_at` to guess which accounts changed: profile edits also bump
-- that timestamp, and an unchanged legal Admin identity must keep its session.

CREATE TABLE IF NOT EXISTS admin_identity_changes (
    user_id      uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    old_login_id text NOT NULL,
    new_login_id text NOT NULL,
    migration_id text NOT NULL,
    changed_at   timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, migration_id)
);

DO $$
DECLARE
    admin_row   record;
    candidate   text;
    seq         integer;
BEGIN
    FOR admin_row IN
        SELECT id, login_id
        FROM users
        WHERE role = 'ADMIN'
          AND login_id !~ '^014014[0-9]{5}$'
        ORDER BY created_at ASC, id ASC
    LOOP
        seq := 1;
        LOOP
            candidate := '014014' || lpad(seq::text, 5, '0');
            EXIT WHEN NOT EXISTS (
                SELECT 1 FROM users WHERE login_id = candidate
            );
            seq := seq + 1;
            IF seq > 99999 THEN
                RAISE EXCEPTION
                    'Kapasitas ID Admin 01401400001–01401499999 habis saat backfill';
            END IF;
        END LOOP;

        INSERT INTO admin_identity_changes
            (user_id, old_login_id, new_login_id, migration_id)
        VALUES
            (admin_row.id, admin_row.login_id, candidate, '002_admin_login_ids.sql')
        ON CONFLICT (user_id, migration_id) DO NOTHING;

        UPDATE users
        SET login_id = candidate,
            updated_at = now()
        WHERE id = admin_row.id;

        -- The old identity's session must not survive the re-identification.
        -- Rows are retained (is_active=false, revoked_at set) as history.
        UPDATE auth_sessions
        SET is_active = false,
            revoked_at = COALESCE(revoked_at, now())
        WHERE user_id = admin_row.id
          AND is_active;
    END LOOP;
END $$;

-- Role-scoped shape rule. GLOBAL uniqueness is already enforced by
-- ux_users_login from migration 001 and is preserved.
ALTER TABLE users
    ADD CONSTRAINT ck_users_admin_login_id
    CHECK (role <> 'ADMIN' OR login_id ~ '^014014[0-9]{5}$');
