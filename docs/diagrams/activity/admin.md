# Activity Diagram — Admin

**Notasi:** Mermaid `flowchart` dengan initial/final state, decision node, dan guard.  
**Cakupan:** AD-A01–AD-A03.

---

## AD-A01 — Membuat Akun

```mermaid
flowchart TD
    Start(["Mulai"]) --> Login["Admin login"]
    Login --> Form["Isi nama, NIM/NIP, role, password sementara"]
    Form --> Valid{"Input valid?"}
    Valid -->|"Tidak"| Bad["422"] --> Form
    Valid -->|"Ya"| Unique{"Login unik?"}
    Unique -->|"Tidak"| Dup["409 LOGIN_ID_TAKEN"] --> Form
    Unique -->|"Ya"| Create["Buat akun + enrollment/teaching assignment"]
    Create --> Tx{"Transaksi berhasil?"}
    Tx -->|"Tidak"| Rollback["500 rollback"] --> End(["Selesai"])
    Tx -->|"Ya"| Audit["Catat audit ACCOUNT_CREATED"] --> End

    classDef startend fill:#C8E6C9,stroke:#2E7D32,color:#1B5E20
    classDef action fill:#E3F2FD,stroke:#1565C0,color:#0D47A1
    classDef decision fill:#FFF3E0,stroke:#EF6C00,color:#E65100
    classDef error fill:#FFEBEE,stroke:#C62828,color:#B71C1C
    class Start,End startend
    class Login,Form,Create,Audit action
    class Valid,Unique,Tx decision
    class Bad,Dup,Rollback error
```

**Guard:** Role hanya tiga kategori; password sementara; auto-enroll/assign.

---

## AD-A02 — Melihat dan Memperbarui Akun

```mermaid
flowchart TD
    Start(["Mulai"]) --> Login["Admin login"]
    Login --> Search["Cari/pilih akun"]
    Search --> Exists{"Akun ada?"}
    Exists -->|"Tidak"| NotFound["404"] --> End(["Selesai"])
    Exists -->|"Ya"| Edit["Ubah nama/role/status/reset password"]
    Edit --> Valid{"Valid dan unik?"}
    Valid -->|"Tidak"| Bad["422 / 409"] --> Edit
    Valid -->|"Ya"| Save["Simpan + audit"]
    Save --> Revoke{"Role/status berubah?"}
    Revoke -->|"Ya"| Sessions["Cabut sesi target"] --> End
    Revoke -->|"Tidak"| End

    classDef startend fill:#C8E6C9,stroke:#2E7D32,color:#1B5E20
    classDef action fill:#E3F2FD,stroke:#1565C0,color:#0D47A1
    classDef decision fill:#FFF3E0,stroke:#EF6C00,color:#E65100
    classDef error fill:#FFEBEE,stroke:#C62828,color:#B71C1C
    class Start,End startend
    class Login,Search,Edit,Save,Sessions action
    class Exists,Valid,Revoke decision
    class NotFound,Bad error
```

**Guard:** Admin tidak dapat mengubah data akademik; last-admin guard.

---

## AD-A03 — Menonaktifkan Akun

```mermaid
flowchart TD
    Start(["Mulai"]) --> Login["Admin login"]
    Login --> Pick["Pilih akun"]
    Pick --> Exists{"Akun ada?"}
    Exists -->|"Tidak"| NotFound["404"] --> End(["Selesai"])
    Exists -->|"Ya"| Guard{"Target self / admin terakhir?"}
    Guard -->|"Ya"| Reject["409 LAST_ADMIN_PROTECTED"] --> End
    Guard -->|"Tidak"| Confirm["Konfirmasi"]
    Confirm --> Tx["Set INACTIVE + revoke sessions + audit"]
    Tx --> Result{"Berhasil?"}
    Result -->|"Tidak"| Rollback["500 rollback"] --> End
    Result -->|"Ya"| Done["Login berikutnya ditolak"] --> End

    classDef startend fill:#C8E6C9,stroke:#2E7D32,color:#1B5E20
    classDef action fill:#E3F2FD,stroke:#1565C0,color:#0D47A1
    classDef decision fill:#FFF3E0,stroke:#EF6C00,color:#E65100
    classDef error fill:#FFEBEE,stroke:#C62828,color:#B71C1C
    class Start,End startend
    class Login,Pick,Confirm,Tx,Done action
    class Exists,Guard,Result decision
    class NotFound,Reject,Rollback error
```

**Guard:** Histori akademik tetap; sesi aktif dicabut.
