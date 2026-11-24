-- 003_admin_session_revoke_and_range.sql — remediation for FR-001/034/036
--
-- Two gaps in migration 002 are closed here WITHOUT editing 001 or 002:
--
-- 1. SESSION REVOCATION. Migration 002 already revokes sessions atomically for
--    every account it re-identifies and records the exact old/new identifiers
--    in `admin_identity_changes`. This migration only has to handle the
--    out-of-range 01401400000 identity that 002 intentionally skipped. It
--    records that change and revokes that account's sessions in this same
--    transaction. It NEVER infers affected accounts from `updated_at`: a
--    profile edit also bumps that timestamp, while an unchanged legal Admin
--    identity must keep its normal session rules.
--
--    Pre-remediation history guard: if an installation already applied the old
--    002 (which had no marker table), the exact affected accounts cannot be
--    reconstructed. Applying this migration without the marker table aborts
--    instead of silently leaving sessions alive or broadly revoking unchanged
--    Admins. The runner fails before the service starts, so traffic is not
--    opened; the Owner must reconcile the legacy installation explicitly.
--
-- 2. RANGE TIGHTENING. The pattern added by 002 accepted 01401400000, but the
--    valid sequence is 00001–99999. The constraint is replaced so the
--    out-of-range identity is rejected; global uniqueness (ux_users_login)
--    is untouched.

DO $$
BEGIN
    IF to_regclass('public.admin_identity_changes') IS NULL THEN
        RAISE EXCEPTION
            'Riwayat migrasi 002 lama tidak memiliki penanda admin_identity_changes; akun yang berubah tidak dapat diidentifikasi. Hentikan deployment, jangan buka trafik, dan rekonsiliasi akun/sesi Admin secara eksplisit sebelum 003.';
    END IF;
END $$;

-- 1) Normalise any Admin that still carries the out-of-range 01401400000
--    identity (002 treated it as "on pattern" and skipped it). The account is
--    re-identified deterministically before the tightened constraint is added,
--    so an existing row cannot block the upgrade.
DO $$
DECLARE
    admin_row   record;
    candidate   text;
    seq         integer;
BEGIN
    FOR admin_row IN
        SELECT id, login_id
        FROM users
        WHERE role = 'ADMIN' AND login_id = '01401400000'
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
                    'Kapasitas ID Admin 01401400001–01401499999 habis saat normalisasi 01401400000';
            END IF;
        END LOOP;

        INSERT INTO admin_identity_changes
            (user_id, old_login_id, new_login_id, migration_id)
        VALUES
            (admin_row.id, admin_row.login_id, candidate, '003_admin_session_revoke_and_range.sql')
        ON CONFLICT (user_id, migration_id) DO NOTHING;

        UPDATE users
        SET login_id = candidate,
            updated_at = now()
        WHERE id = admin_row.id;

        UPDATE auth_sessions
        SET is_active = false,
            revoked_at = COALESCE(revoked_at, now())
        WHERE user_id = admin_row.id
          AND is_active;
    END LOOP;
END $$;

-- 3) Range 00001–99999: reject the only invalid five-digit sequence.
ALTER TABLE users DROP CONSTRAINT IF EXISTS ck_users_admin_login_id;
ALTER TABLE users
    ADD CONSTRAINT ck_users_admin_login_id
    CHECK (
        role <> 'ADMIN'
        OR (login_id ~ '^014014[0-9]{5}$' AND login_id <> '01401400000')
    );
