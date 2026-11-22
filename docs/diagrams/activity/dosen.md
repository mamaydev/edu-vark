# Activity Diagram — Dosen

**Notasi:** Mermaid `flowchart` dengan initial/final state, decision node, dan guard.  
**Cakupan:** AD-D01–AD-D05.

---

## AD-D01 — Mengelola dan Menerbitkan Materi

```mermaid
flowchart TD
    Start(["Mulai"]) --> Login["Dosen login + assigned class"]
    Login --> Menu["Buka kelola topik"]
    Menu --> Action{"Aksi?"}
    Action -->|"Buat/Ubah"| Input["Isi data topik"]
    Input --> Valid{"Input valid?"}
    Valid -->|"Tidak"| Bad["422"] --> Input
    Valid -->|"Ya"| Save["Simpan topik"]
    Action -->|"Urutkan"| Order["Ubah sort_order"] --> Save
    Action -->|"Terbitkan"| Publish["Set status terbit"]
    Action -->|"Unpublish"| Unpub["Set draft/unpublish"]
    Action -->|"Hapus"| HasData{"Ada submission/assessment?"}
    HasData -->|"Ya"| Reject["409; sarankan arsip"] --> Menu
    HasData -->|"Tidak"| Soft["Soft delete"]
    Save --> Done["Sukses"]
    Publish --> Done
    Unpub --> Done
    Soft --> Done
    Done --> End(["Selesai"])

    classDef startend fill:#C8E6C9,stroke:#2E7D32,color:#1B5E20
    classDef action fill:#E3F2FD,stroke:#1565C0,color:#0D47A1
    classDef decision fill:#FFF3E0,stroke:#EF6C00,color:#E65100
    classDef error fill:#FFEBEE,stroke:#C62828,color:#B71C1C
    class Start,End startend
    class Login,Menu,Input,Order,Save,Publish,Unpub,Soft,Done action
    class Action,Valid,HasData decision
    class Bad,Reject error
```

**Guard:** Publikasi boleh tanpa materi lengkap; topik dengan data akademik tidak di-hard-delete.

---

## AD-D02 — Membuat Studi Kasus dan Deadline

```mermaid
flowchart TD
    Start(["Mulai"]) --> Pick["Pilih topik"]
    Pick --> Auth{"Dosen berwenang?"}
    Auth -->|"Tidak"| Deny["403 CLASS_FORBIDDEN"] --> End(["Selesai"])
    Auth -->|"Ya"| Existing{"Studi kasus sudah ada?"}
    Existing -->|"Ya"| Subs{"Sudah ada submission?"}
    Subs -->|"Ya"| Locked["409 DEADLINE_LOCKED"] --> End
    Subs -->|"Tidak"| Form["Isi teori, instruksi, deadline WIB"]
    Existing -->|"Tidak"| Form
    Form --> Valid{"Deadline valid dan masuk akal?"}
    Valid -->|"Tidak"| Bad["422 DEADLINE_INVALID"] --> Form
    Valid -->|"Ya"| Save["Simpan UTC + audit"]
    Save --> Show["Tampilkan ke mahasiswa Kinesthetic"] --> End

    classDef startend fill:#C8E6C9,stroke:#2E7D32,color:#1B5E20
    classDef action fill:#E3F2FD,stroke:#1565C0,color:#0D47A1
    classDef decision fill:#FFF3E0,stroke:#EF6C00,color:#E65100
    classDef error fill:#FFEBEE,stroke:#C62828,color:#B71C1C
    class Start,End startend
    class Pick,Form,Save,Show action
    class Auth,Existing,Subs,Valid decision
    class Deny,Locked,Bad error
```

**Guard:** Satu kasus per topik; deadline terkunci setelah submission pertama.

---

## AD-D03 — Melakukan Penilaian Final

```mermaid
flowchart TD
    Start(["Mulai"]) --> Open["Buka submission"]
    Open --> Auth{"Berwenang dan belum dinilai?"}
    Auth -->|"Tidak"| Deny["403 / 409"] --> End(["Selesai"])
    Auth -->|"Ya"| Input["Masukkan nilai 0-100 + komentar opsional"]
    Input --> Valid{"Valid?"}
    Valid -->|"Tidak"| Bad["422"] --> Input
    Valid -->|"Ya"| Confirm["Konfirmasi final"]
    Confirm --> Tx["Insert assessment + audit dalam transaksi"]
    Tx --> Result{"Berhasil?"}
    Result -->|"Tidak, unique"| Conflict["409 ALREADY_ASSESSED"] --> End
    Result -->|"Tidak, audit"| Rollback["500 rollback; submission utuh"] --> End
    Result -->|"Ya"| Visible["Mahasiswa langsung melihat nilai + komentar"] --> End

    classDef startend fill:#C8E6C9,stroke:#2E7D32,color:#1B5E20
    classDef action fill:#E3F2FD,stroke:#1565C0,color:#0D47A1
    classDef decision fill:#FFF3E0,stroke:#EF6C00,color:#E65100
    classDef error fill:#FFEBEE,stroke:#C62828,color:#B71C1C
    class Start,End startend
    class Open,Input,Confirm,Tx,Visible action
    class Auth,Valid,Result decision
    class Deny,Bad,Conflict,Rollback error
```

**Guard:** Nilai final; tidak ada update/delete; admin tidak boleh menilai.

---

## AD-D04 — Melihat Grafik Pemahaman

```mermaid
flowchart TD
    Start(["Mulai"]) --> Pick["Pilih topik/kategori"]
    Pick --> Auth{"Dosen berwenang?"}
    Auth -->|"Tidak"| Deny["403 CLASS_FORBIDDEN"] --> End(["Selesai"])
    Auth -->|"Ya"| Fetch["Ambil feedback terbaru per mahasiswa-topik-kategori"]
    Fetch --> Data{"Ada data?"}
    Data -->|"Tidak"| Empty["Empty state; n=0"] --> End
    Data -->|"Ya"| Calc["Hitung mean, n, histogram"]
    Calc --> NoFb["Hitung mahasiswa belum feedback terpisah"]
    NoFb --> Kin["Ambil assessment Kinesthetic terpisah"]
    Kin --> Show["Tampilkan grafik + definisi agregasi"] --> End

    classDef startend fill:#C8E6C9,stroke:#2E7D32,color:#1B5E20
    classDef action fill:#E3F2FD,stroke:#1565C0,color:#0D47A1
    classDef decision fill:#FFF3E0,stroke:#EF6C00,color:#E65100
    classDef error fill:#FFEBEE,stroke:#C62828,color:#B71C1C
    class Start,End startend
    class Pick,Fetch,Calc,NoFb,Kin,Show,Empty action
    class Auth,Data decision
    class Deny error
```

**Guard:** Jangan mencampur rerata subjektif dengan nilai objektif Kinesthetic.

---

## AD-D05 — Memeriksa Pengumpulan Kinesthetic

```mermaid
flowchart TD
    Start(["Mulai"]) --> Pick["Pilih studi kasus"]
    Pick --> Auth{"Dosen berwenang?"}
    Auth -->|"Tidak"| Deny["403 CLASS_FORBIDDEN"] --> End(["Selesai"])
    Auth -->|"Ya"| List["Ambil daftar mahasiswa + status"]
    List --> Empty{"Ada mahasiswa?"}
    Empty -->|"Tidak"| EmptyState["Empty state"] --> End
    Empty -->|"Ya"| Show["Tampilkan status tiap mahasiswa"]
    Show --> Open{"Buka jawaban?"}
    Open -->|"Tidak"| End
    Open -->|"Ya"| Detail["Tampilkan teks + waktu (read-only)"] --> End

    classDef startend fill:#C8E6C9,stroke:#2E7D32,color:#1B5E20
    classDef action fill:#E3F2FD,stroke:#1565C0,color:#0D47A1
    classDef decision fill:#FFF3E0,stroke:#EF6C00,color:#E65100
    classDef error fill:#FFEBEE,stroke:#C62828,color:#B71C1C
    class Start,End startend
    class Pick,List,Show,Detail,EmptyState action
    class Auth,Empty,Open decision
    class Deny error
```

**Guard:** `Tidak Mengumpulkan` bukan nilai 0; jawaban read-only.
