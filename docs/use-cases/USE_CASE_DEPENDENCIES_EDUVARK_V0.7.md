# Use Case Dependencies — EduVARK v0.7

**Tujuan:** Memperlihatkan dependensi **data dan otorisasi** antar use case, bukan urutan wajib eksekusi.  
**Prinsip:** Opsi A — prasyarat dinilai dari keadaan data saat use case dijalankan. `<<include>>` dan `<<extend>>` tidak dipakai untuk menyatakan “objek harus sudah ada”.

## Legenda

| Bentuk | Arti |
|---|---|
| Persegi membulat | Use case |
| Persegi | Data/objek/guard |
| Garis penuh | Dependensi utama |
| Garis putus-putus | Dependensi opsional/kontekstual |

> Diagram ini **bukan** pengganti UML Use Case Diagram. Use Case Diagram ada di [`../diagrams/use-case/`](../diagrams/use-case/).

---

## 1. Admin

```mermaid
flowchart TD
    A0["Admin terautentikasi"] --> A01["UC-A01: Membuat Akun"]
    A01 --> ACC["Akun target tersedia"]
    A01 --> ENR["Enrollment / teaching assignment"]
    ACC --> A02["UC-A02: Melihat & Memperbarui Akun"]
    ACC --> A03["UC-A03: Menonaktifkan Akun"]
    A02 --> ROLE["Perubahan role/status"]
    ROLE --> SESS["Sesi target dicabut"]
    A03 --> INACT["Akun INACTIVE"]
    INACT --> SESS
    GUARD["Guard: bukan self / admin terakhir"] --> A03
    LIMIT["Guard: admin tidak mengakses data akademik"] -.-> A02
    LIMIT -.-> A03

    classDef actor fill:#E3F2FD,stroke:#1565C0,color:#0D47A1
    classDef uc fill:#E8F5E9,stroke:#2E7D32,color:#1B5E20
    classDef data fill:#FFF3E0,stroke:#EF6C00,color:#E65100
    classDef guard fill:#F3E5F5,stroke:#6A1B9A,color:#4A148C
    class A0 actor
    class A01,A02,A03 uc
    class ACC,ENR,ROLE,SESS,INACT data
    class GUARD,LIMIT guard
```

**Catatan Admin**
- Login/logout staf memakai **UC-M01/M07** (D-18); staf tidak memilih VARK.
- `UC-A02` dan `UC-A03` hanya membutuhkan akun target yang sudah ada; tidak wajib menjalankan `UC-A01` pada sesi yang sama.
- `UC-A03` tidak mewajibkan `UC-A02`; keduanya bertemu pada data “akun target”.
- Perubahan role/status mencabut sesi target (D-11).
- Admin tidak memiliki edge ke materi/feedback/nilai.

---

## 2. Dosen

```mermaid
flowchart TD
    D0["Dosen terautentikasi + assigned class"] --> D01["UC-D01: Mengelola Topik"]
    D01 --> TOPIC["Topik tersedia"]
    TOPIC --> D02["UC-D02: Mengelola Materi"]
    TOPIC --> D03["UC-D03: Membuat Studi Kasus"]
    D02 --> MAT["Materi per kategori VARK"]
    D03 --> CASE["Studi kasus + deadline"]
    CASE --> D04["UC-D04: Memeriksa Pengumpulan"]
    D04 -.-> SUB["Submission tersedia"]
    SUB --> D05["UC-D05: Menilai Final"]
    D0 --> D06["UC-D06: Grafik Pemahaman"]
    FB["Feedback V/A/R"] -.-> D06
    ASS["Assessment Kinesthetic"] -.-> D06
    MAT -. "tidak wajib untuk grafik" .-> D06
    GUARD["Guard: hanya kelas yang diampu"] --> D01
    GUARD --> D06
    GUARD --> D05
    D05 --> ASS

    classDef actor fill:#E3F2FD,stroke:#1565C0,color:#0D47A1
    classDef uc fill:#E8F5E9,stroke:#2E7D32,color:#1B5E20
    classDef data fill:#FFF3E0,stroke:#EF6C00,color:#E65100
    classDef guard fill:#F3E5F5,stroke:#6A1B9A,color:#4A148C
    class D0 actor
    class D01,D02,D03,D04,D05,D06 uc
    class TOPIC,MAT,CASE,SUB,FB,ASS data
    class GUARD guard
```

**Catatan Dosen**
- Login/logout staf memakai **UC-M01/M07** (D-18); staf tidak memilih VARK.
- `UC-D02` dan `UC-D03` membutuhkan topik yang sudah ada, tetapi tidak mewajibkan `UC-D01` pada sesi yang sama.
- `UC-D04` dapat menampilkan daftar kosong/`Tidak Mengumpulkan` tanpa submission.
- `UC-D05` membutuhkan submission yang belum dinilai; bukan seluruh rantai pembuatan topik/kasus.
- `UC-D06` tidak mensyaratkan pembuatan topik, submission, atau penilaian pada sesi yang sama; jika data kosong, tampil empty state.

---

## 3. Mahasiswa

```mermaid
flowchart TD
    M0["Mahasiswa membuka aplikasi"] --> M01["UC-M01: Login + pilih VARK"]
    M01 --> SESS["Sesi + VARK aktif"]
    SESS --> M02["UC-M02: Daftar Topik"]
    ENR["Enrollment"] --> M02
    M02 --> TOPIC["Topik terbit + urutan"]
    TOPIC --> M03["UC-M03: Akses Materi"]
    M03 --> MAT["Materi kategori sesi"]
    MAT --> M04["UC-M04: Feedback V/A/R"]
    M03 -. "sesi Kinesthetic" .-> CASE["Studi kasus Kinesthetic"]
    CASE --> M05["UC-M05: Submit Kinesthetic"]
    M05 --> SUB["Submission final"]
    SUB --> M06["UC-M06: Lihat Status/Hasil"]
    M03 -. "tanpa materi" .-> MSG["Pesan materi belum tersedia"]
    M03 -. "tanpa kasus" .-> CMSG["Pesan studi kasus belum tersedia"]
    M06 -. "tanpa submission" .-> STATUS["Status Tidak Mengumpulkan"]
    M01 --> M07["UC-M07: Logout"]
    M07 --> ENDSESS["Sesi berakhir; VARK dilepas"]
    ENDSESS -. "login berikutnya" .-> M01
    GUARD["Guard: VARK aktif + enrollment"] --> M02
    GUARD --> M03

    classDef actor fill:#E3F2FD,stroke:#1565C0,color:#0D47A1
    classDef uc fill:#E8F5E9,stroke:#2E7D32,color:#1B5E20
    classDef data fill:#FFF3E0,stroke:#EF6C00,color:#E65100
    classDef guard fill:#F3E5F5,stroke:#6A1B9A,color:#4A148C
    class M0 actor
    class M01,M02,M03,M04,M05,M06,M07 uc
    class SESS,ENR,TOPIC,MAT,CASE,SUB,MSG,CMSG,STATUS,ENDSESS data
    class GUARD guard
```

**Catatan Mahasiswa**
- `UC-M04` hanya untuk V/A/R dan materi tersedia; sesi Kinesthetic diarahkan ke `UC-M05`.
- `UC-M05` membutuhkan kategori Kinesthetic, kasus, belum submit, dan deadline.
- `UC-M06` **tidak** membutuhkan `UC-M05` berhasil; status `Belum`/`Tidak Mengumpulkan` tetap dapat ditampilkan.
- `UC-M07` boleh kapan saja selama sesi aktif.

---

## 4. Lintas role (konteks, bukan dependensi wajib)

```mermaid
flowchart LR
    ADM["Admin: UC-A01/A02"] --> ACC["Akun + enrollment"]
    ACC --> LEC["Dosen: UC-D01/D02/D03"]
    LEC --> CONTENT["Topik + materi + kasus"]
    CONTENT --> STU["Mahasiswa: UC-M01–M05"]
    STU --> WORK["Feedback + submission"]
    WORK --> GRADE["Dosen: UC-D04/D05"]
    GRADE --> RESULT["Mahasiswa: UC-M06"]
    GRADE --> ANA["Dosen: UC-D06"]

    classDef data fill:#FFF3E0,stroke:#EF6C00,color:#E65100
    classDef uc fill:#E8F5E9,stroke:#2E7D32,color:#1B5E20
    class ACC,CONTENT,WORK,RESULT data
    class ADM,LEC,STU,GRADE,ANA uc
```

> Diagram lintas role hanya menunjukkan aliran nilai. Tidak ada `<<include>>` antar-role; masing-masing use case tetap dijalankan oleh aktornya sendiri dengan otorisasi masing-masing.

---

## 5. Aturan dependensi yang dipertahankan

1. Tidak ada rantai wajib `UC-A01 → UC-A02 → UC-A03`.
2. Tidak ada `<<include>>` untuk “objek harus sudah ada”.
3. Tidak ada `<<extend>>` untuk alur utama.
4. Guard data (akun, topik, submission, VARK) ditempatkan sebagai precondition.
5. Use case yang dapat menampilkan empty state (`UC-D04`, `UC-D06`, `UC-M06`) tidak diwajibkan memiliki data lengkap.
