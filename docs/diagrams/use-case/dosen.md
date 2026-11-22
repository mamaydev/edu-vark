# Use Case Diagram — Dosen

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

actor "Dosen" as Dosen

rectangle "EduVARK" {
  usecase "UC-D01\nMengelola Topik IBD" as D01
  usecase "UC-D02\nMengelola Format Materi VARK" as D02
  usecase "UC-D03\nMembuat Studi Kasus dan Deadline" as D03
  usecase "UC-D04\nMemeriksa Pengumpulan Kinesthetic" as D04
  usecase "UC-D05\nMemberikan Penilaian Final" as D05
  usecase "UC-D06\nMelihat Grafik Pemahaman" as D06
  usecase "UC-M01\nLogin (VARK: mahasiswa)" as M01
  usecase "UC-M07\nLogout" as M07
}

Dosen --> D01
Dosen --> D02
Dosen --> D03
Dosen --> D04
Dosen --> D05
Dosen --> D06
Dosen --> M01
Dosen --> M07

note right of D01
  Precondition: dosen terautentikasi
  dan di-assign ke kelas.
end note

note right of D02
  Precondition: topik target sudah ada.
  Tidak wajib membuat topik baru
  pada sesi yang sama.
end note

note right of D03
  Precondition: topik target ada.
  Deadline terkunci setelah
  submission pertama (D-09).
end note

note right of D04
  Dapat menampilkan daftar kosong /
  Tidak Mengumpulkan tanpa submission.
end note

note right of D05
  Precondition: submission ada dan
  belum memiliki assessment.
  Penilaian pertama bersifat final.
end note

note right of D06
  Tidak mensyaratkan pembuatan topik,
  submission, atau penilaian pada
  sesi yang sama; empty state jika kosong.
end note

note bottom of M01
  D-18: dosen login tanpa memilih VARK.
end note

@enduml
```

## Catatan

- Tidak ada relasi `<<include>>`/`<<extend>>`; semua prasyarat adalah guard data/otorisasi.
- `UC-D02` dan `UC-D03` hanya membutuhkan topik yang sudah ada.
- Login dosen adalah *shared precondition* (lihat OQ-15).
