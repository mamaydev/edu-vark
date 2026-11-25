-- Niat penggantian berkas materi yang persisten.
--
-- Baris dicatat SEBELUM objek pengganti ditulis ke penyimpanan, di dalam
-- transaksi terpisah dari perubahan metadata materi. Tujuannya:
--   * bila proses berhenti setelah objek tertulis tetapi sebelum metadata
--     berubah, worker rekonsiliasi dapat menemukan kunci pengganti yang
--     tercatat dan membersihkannya secara idempoten;
--   * bila COMMIT metadata berhasil tetapi konfirmasi ke proses hilang,
--     baris berstatus ACTIVE sudah ikut ter-commit sehingga objek pengganti
--     tidak pernah dianggap sampah;
--   * bila upload/commit ambigu, baris PENDING tetap ada dan diselesaikan
--     berdasarkan keadaan `materials.object_key` yang benar-benar tersimpan.
--
-- `lease_token`/`lease_expires_at` mencegah rekonsiliasi menyerobot unggahan
-- yang masih berjalan. Klaim hanya boleh diambil alih setelah lease habis dan
-- setiap penyelesaian memakai pembaruan bersyarat pada identitas klaim.
CREATE TABLE IF NOT EXISTS material_file_replacements (
    id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    material_id           uuid NOT NULL REFERENCES materials(id) ON DELETE RESTRICT,
    requested_by          uuid REFERENCES users(id) ON DELETE SET NULL,
    old_object_key        text NOT NULL,
    new_object_key        text NOT NULL,
    state                 text NOT NULL DEFAULT 'PENDING',
    lease_token           uuid,
    lease_expires_at      timestamptz,
    old_deletion_job_id   uuid REFERENCES material_object_deletions(id) ON DELETE SET NULL,
    cleanup_job_id        uuid REFERENCES material_object_deletions(id) ON DELETE SET NULL,
    attempts              integer NOT NULL DEFAULT 0,
    last_error            text,
    created_at            timestamptz NOT NULL DEFAULT now(),
    updated_at            timestamptz NOT NULL DEFAULT now(),
    resolved_at           timestamptz,
    CONSTRAINT ck_mfr_state CHECK (
        state IN ('PENDING', 'ACTIVE', 'ABANDONED')
    ),
    CONSTRAINT ck_mfr_attempts CHECK (attempts >= 0),
    CONSTRAINT ck_mfr_distinct_keys CHECK (old_object_key <> new_object_key)
);

-- Setiap unggahan pengganti memakai kunci unik; unique index mencegah dua
-- operasi berbeda mencatat kunci yang sama (termasuk salah input).
CREATE UNIQUE INDEX IF NOT EXISTS ux_mfr_new_object_key
    ON material_file_replacements (new_object_key);

CREATE INDEX IF NOT EXISTS ix_mfr_pending_lease
    ON material_file_replacements (lease_expires_at)
    WHERE state = 'PENDING';

CREATE INDEX IF NOT EXISTS ix_mfr_material
    ON material_file_replacements (material_id, created_at DESC);

DROP TRIGGER IF EXISTS trg_material_file_replacements_updated ON material_file_replacements;
CREATE TRIGGER trg_material_file_replacements_updated
BEFORE UPDATE ON material_file_replacements
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Objek pengganti yang batal dipakai dibersihkan lewat antrean yang sama
-- dengan penghapusan objek lain (retry otomatis, cek referensi aktif).
ALTER TABLE material_object_deletions
    DROP CONSTRAINT IF EXISTS ck_mod_reason;

ALTER TABLE material_object_deletions
    ADD CONSTRAINT ck_mod_reason CHECK (
        reason IN (
            'MATERIAL_DELETED',
            'FILE_REPLACED',
            'BACKLOG_CLEANUP',
            'REPLACEMENT_ABANDONED'
        )
    );
