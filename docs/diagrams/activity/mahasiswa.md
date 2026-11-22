# Activity Diagram — Mahasiswa

**Notasi:** Mermaid `flowchart` dengan initial/final state, decision node, dan guard.  
**Cakupan:** AD-M01–AD-M05.

---

## AD-M01 — Login dan Pemilihan VARK

```mermaid
flowchart TD
    Start(["Mulai"]) --> Open["Buka aplikasi"]
    Open --> HasSession{"Sesi aktif?"}
    HasSession -->|"Ya"| Dashboard["Lanjut ke dashboard"]
    HasSession -->|"Tidak"| Cred["Masukkan NIM/NIP + password"]
    Cred --> Valid{"Kredensial valid?"}
    Valid -->|"Tidak"| ErrCred["401 pesan generik"] --> Cred
    Valid -->|"Ya"| Active{"Akun aktif?"}
    Active -->|"Tidak"| ErrInactive["403 akun nonaktif"] --> End(["Selesai"])
    Active -->|"Ya"| Temp{"Password sementara?"}
    Temp -->|"Ya"| Change["Wajib ganti password"]
    Change --> ChangeOK{"Berhasil?"}
    ChangeOK -->|"Tidak"| Change
    ChangeOK -->|"Ya"| Choices["Tampilkan 4 pilihan VARK"]
    Temp -->|"Tidak"| Choices
    Choices --> Pick{"Pilih satu VARK?"}
    Pick -->|"Tidak"| Locked["Dashboard terkunci; 428 VARK_REQUIRED"] --> Choices
    Pick -->|"Ya"| Save["Simpan VARK + cabut sesi lama"]
    Save --> Dash["Dashboard mahasiswa"]
    Dash --> End
    Dashboard --> End

    classDef startend fill:#C8E6C9,stroke:#2E7D32,color:#1B5E20
    classDef action fill:#E3F2FD,stroke:#1565C0,color:#0D47A1
    classDef decision fill:#FFF3E0,stroke:#EF6C00,color:#E65100
    classDef error fill:#FFEBEE,stroke:#C62828,color:#B71C1C
    class Start,End startend
    class Open,Cred,Change,Choices,Save,Dash,Dashboard action
    class HasSession,Valid,Active,Temp,ChangeOK,Pick decision
    class ErrCred,ErrInactive,Locked error
```

**Guard:** VARK wajib sebelum endpoint akademik; satu sesi aktif (D-04).

---

## AD-M02 — Mengakses Materi Berdasarkan Preferensi

```mermaid
flowchart TD
    Start(["Mulai"]) --> List["Buka daftar topik"]
    List --> Guard{"Sesi + VARK + enrollment valid?"}
    Guard -->|"Tidak"| Deny["401 / 403 / 428"] --> End(["Selesai"])
    Guard -->|"Ya"| Fetch["Ambil topik terbit urut sort_order"]
    Fetch --> Empty{"Ada topik?"}
    Empty -->|"Tidak"| EmptyState["Empty state"] --> End
    Empty -->|"Ya"| Choose["Pilih topik"]
    Choose --> Topic{"Topik terbit dan terdaftar?"}
    Topic -->|"Tidak"| NotFound["404 / 403"] --> End
    Topic -->|"Ya"| Mat{"Materi kategori sesi ada?"}
    Mat -->|"Tidak"| Msg["Pesan persis: materi belum tersedia, harap hubungi dosen terlebih dahulu"] --> End
    Mat -->|"Ya"| URL["Buat signed URL 5 menit"]
    URL --> Show["Tampilkan materi"] --> End

    classDef startend fill:#C8E6C9,stroke:#2E7D32,color:#1B5E20
    classDef action fill:#E3F2FD,stroke:#1565C0,color:#0D47A1
    classDef decision fill:#FFF3E0,stroke:#EF6C00,color:#E65100
    classDef error fill:#FFEBEE,stroke:#C62828,color:#B71C1C
    class Start,End startend
    class List,Fetch,Choose,URL,Show,EmptyState action
    class Guard,Empty,Topic,Mat decision
    class Deny,NotFound,Msg error
```

**Guard:** Hanya kategori sesi; URL kategori lain ditolak backend.

---

## AD-M03 — Memberikan atau Memperbarui Feedback

```mermaid
flowchart TD
    Start(["Mulai"]) --> Open["Buka materi V/A/R"]
    Open --> Vark{"VARK V/A/R?"}
    Vark -->|"Tidak"| NoSlider["403; slider tidak tersedia"] --> End(["Selesai"])
    Vark -->|"Ya"| Mat{"Materi tersedia?"}
    Mat -->|"Tidak"| Msg["404 pesan materi belum tersedia"] --> End
    Mat -->|"Ya"| Get["Ambil feedback terbaru"]
    Get --> Exists{"Ada feedback?"}
    Exists -->|"Ya"| Show["Tampilkan nilai terakhir"]
    Exists -->|"Tidak"| Null["Tampilkan belum ada feedback"]
    Show --> Slider["Atur slider integer 0-100"]
    Null --> Slider
    Slider --> Save["Tekan Simpan"]
    Save --> Range{"Integer 0-100?"}
    Range -->|"Tidak"| Bad["422 INVALID_FEEDBACK_VALUE"] --> Slider
    Range -->|"Ya"| Upsert["Upsert feedback triple"]
    Upsert --> OK["200 sukses"] --> End

    classDef startend fill:#C8E6C9,stroke:#2E7D32,color:#1B5E20
    classDef action fill:#E3F2FD,stroke:#1565C0,color:#0D47A1
    classDef decision fill:#FFF3E0,stroke:#EF6C00,color:#E65100
    classDef error fill:#FFEBEE,stroke:#C62828,color:#B71C1C
    class Start,End startend
    class Open,Get,Show,Null,Slider,Save,Upsert,OK action
    class Vark,Mat,Exists,Range decision
    class NoSlider,Msg,Bad error
```

**Guard:** Feedback per topik–kategori, bukan per materi; kategori lain tidak berubah.

---

## AD-M04 — Mengumpulkan Studi Kasus Kinesthetic

```mermaid
flowchart TD
    Start(["Mulai"]) --> Open["Buka studi kasus"]
    Open --> Vark{"VARK Kinesthetic?"}
    Vark -->|"Tidak"| Deny["403 VARK_MISMATCH"] --> End(["Selesai"])
    Vark -->|"Ya"| Case{"Studi kasus ada?"}
    Case -->|"Tidak"| NoCase["Pesan: studi kasus belum tersedia"] --> End
    Case -->|"Ya"| Deadline{"server_now <= deadline?"}
    Deadline -->|"Tidak"| Late["409 DEADLINE_PASSED"] --> End
    Deadline -->|"Ya"| Type["Ketik jawaban; tanpa draft"]
    Type --> Submit["Tekan Kirim"]
    Submit --> Len{"Panjang 1-10000?"}
    Len -->|"Tidak"| Bad["422 ANSWER_LENGTH_INVALID"] --> Type
    Len -->|"Ya"| Dup{"Sudah submit?"}
    Dup -->|"Ya"| Conflict["409 ALREADY_SUBMITTED"] --> End
    Dup -->|"Tidak"| Atomic["Transaksi atomik: cek deadline + insert"]
    Atomic --> Result{"Berhasil?"}
    Result -->|"Tidak, unique"| Conflict
    Result -->|"Tidak, deadline"| Late
    Result -->|"Ya"| Waiting["Status Menunggu Penilaian"] --> End

    classDef startend fill:#C8E6C9,stroke:#2E7D32,color:#1B5E20
    classDef action fill:#E3F2FD,stroke:#1565C0,color:#0D47A1
    classDef decision fill:#FFF3E0,stroke:#EF6C00,color:#E65100
    classDef error fill:#FFEBEE,stroke:#C62828,color:#B71C1C
    class Start,End startend
    class Open,Type,Submit,Atomic,Waiting action
    class Vark,Case,Deadline,Len,Dup,Result decision
    class Deny,NoCase,Late,Bad,Conflict error
```

**Guard:** Deadline inklusif; unique submission; tidak ada draft/revisi.

---

## AD-M05 — Melihat Status dan Hasil Penilaian

```mermaid
flowchart TD
    Start(["Mulai"]) --> Open["Buka studi kasus"]
    Open --> Access{"Akses valid?"}
    Access -->|"Tidak"| Deny["403 / 404"] --> End(["Selesai"])
    Access -->|"Ya"| Sub{"Ada submission?"}
    Sub -->|"Tidak"| Deadline{"Deadline lewat?"}
    Deadline -->|"Tidak"| Belum["Status Belum Mengumpulkan"] --> End
    Deadline -->|"Ya"| Tidak["Status Tidak Mengumpulkan; bukan nilai 0"] --> End
    Sub -->|"Ya"| Assess{"Ada assessment?"}
    Assess -->|"Tidak"| Waiting["Status Menunggu Penilaian"] --> End
    Assess -->|"Ya"| Done["Tampilkan nilai 0-100 + komentar"] --> End

    classDef startend fill:#C8E6C9,stroke:#2E7D32,color:#1B5E20
    classDef action fill:#E3F2FD,stroke:#1565C0,color:#0D47A1
    classDef decision fill:#FFF3E0,stroke:#EF6C00,color:#E65100
    classDef error fill:#FFEBEE,stroke:#C62828,color:#B71C1C
    class Start,End startend
    class Open,Belum,Tidak,Waiting,Done action
    class Access,Sub,Deadline,Assess decision
    class Deny error
```

**Guard:** Status dihitung server; tidak ada opsi revisi.
