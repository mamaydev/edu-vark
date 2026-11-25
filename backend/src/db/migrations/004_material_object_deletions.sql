-- Antrean penghapusan objek penyimpanan (R2 / penyimpanan lokal pengembangan).
--
-- Fitur hapus materi dan ganti berkas mencatat niat penghapusan objek di dalam
-- transaksi yang sama dengan perubahan data aplikasi. Percobaan fisik pertama
-- langsung dijalankan setelah commit; bila penyimpanan gagal atau hasilnya
-- belum pasti, baris PENDING di tabel ini dicoba ulang otomatis oleh worker.
-- Backlog soft delete lama tidak memiliki baris di tabel ini sehingga restart
-- backend tidak pernah memicu pembersihan backlog secara otomatis.
CREATE TABLE IF NOT EXISTS material_object_deletions (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    object_key      text NOT NULL,
    material_id     uuid REFERENCES materials(id) ON DELETE RESTRICT,
    requested_by    uuid REFERENCES users(id) ON DELETE SET NULL,
    reason          text NOT NULL,
    state           text NOT NULL DEFAULT 'PENDING',
    attempts        integer NOT NULL DEFAULT 0,
    next_attempt_at timestamptz NOT NULL DEFAULT now(),
    last_error      text,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    completed_at    timestamptz,
    CONSTRAINT ck_mod_state CHECK (
        state IN ('PENDING', 'COMPLETED', 'ABSENT', 'FAILED', 'SKIPPED')
    ),
    CONSTRAINT ck_mod_reason CHECK (
        reason IN ('MATERIAL_DELETED', 'FILE_REPLACED', 'BACKLOG_CLEANUP')
    ),
    CONSTRAINT ck_mod_attempts CHECK (attempts >= 0)
);

-- Satu objek hanya boleh punya satu pekerjaan PENDING agar percobaan konkuren
-- tidak menggandakan penghapusan. Baris yang sudah selesai tetap disimpan
-- sebagai jejak audit operasional.
CREATE UNIQUE INDEX IF NOT EXISTS ux_mod_pending_object_key
    ON material_object_deletions (object_key)
    WHERE state = 'PENDING';

CREATE INDEX IF NOT EXISTS ix_mod_due
    ON material_object_deletions (next_attempt_at)
    WHERE state = 'PENDING';

CREATE INDEX IF NOT EXISTS ix_mod_material
    ON material_object_deletions (material_id, created_at DESC);

DROP TRIGGER IF EXISTS trg_material_object_deletions_updated ON material_object_deletions;
CREATE TRIGGER trg_material_object_deletions_updated
BEFORE UPDATE ON material_object_deletions
FOR EACH ROW EXECUTE FUNCTION set_updated_at();
