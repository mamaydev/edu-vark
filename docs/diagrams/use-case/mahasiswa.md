# Use Case Diagram — Mahasiswa

**Notasi:** PlantUML (UML use case).  
**Source:** blok di bawah dapat diedit dan dirender ulang.

```plantuml
@startuml
left to right direction
skinparam packageStyle rectangle
skinparam usecase {
  BackgroundColor #E8F5E9
  BorderColor #2E7D32
}
skinparam actor {
  BackgroundColor #E3F2FD
  BorderColor #1565C0
}

actor "Mahasiswa" as Mahasiswa

rectangle "EduVARK" {
  usecase "UC-M01\nLogin dan Memilih VARK" as M01
  usecase "UC-M02\nMelihat Daftar Topik" as M02
  usecase "UC-M03\nMengakses Materi Sesuai VARK" as M03
  usecase "UC-M04\nMengisi atau Memperbarui Feedback" as M04
  usecase "UC-M05\nMengumpulkan Studi Kasus Kinesthetic" as M05
  usecase "UC-M06\nMelihat Status dan Hasil Koreksi" as M06
  usecase "UC-M07\nLogout" as M07
}

Mahasiswa --> M01
Mahasiswa --> M02
Mahasiswa --> M03
Mahasiswa --> M04
Mahasiswa --> M05
Mahasiswa --> M06
Mahasiswa --> M07

note right of M01
  Setelah login, mahasiswa wajib memilih
  tepat satu VARK; kategori dikunci
  sampai logout.
end note

note right of M02
  Precondition: VARK aktif + enrollment.
  Hanya topik terbit yang tampil.
end note

note right of M03
  Precondition: topik terbit + materi
  kategori sesi. Jika kosong, tampil
  pesan persis FR-016.
end note

note right of M04
  Hanya V/A/R dan materi tersedia.
  Satu feedback terbaru per
  mahasiswa-topik-kategori.
end note

note right of M05
  Hanya Kinesthetic; satu submission;
  tanpa draft; deadline inklusif.
end note

note right of M06
  Tidak membutuhkan submission berhasil;
  status Tidak Mengumpulkan tetap tampil.
end note

note right of M07
  Mengakhiri sesi dan melepas VARK;
  login berikutnya memilih ulang.
end note

@enduml
```

## Catatan

- Tidak ada relasi `<<include>>`/`<<extend>>`; prasyarat adalah guard.
- `UC-M04` dan `UC-M05` adalah jalur berbeda berdasarkan kategori sesi.
- Login mahasiswa mencakup pemilihan VARK; login staf tidak termasuk di sini (OQ-15).
