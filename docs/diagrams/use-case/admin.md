# Use Case Diagram — Admin

**Notasi:** PlantUML (UML use case). Mermaid tidak dipakai karena tidak mendukung notasi use case UML secara tepat.  
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

actor "Admin" as Admin

rectangle "EduVARK" {
  usecase "UC-A01\nMembuat Akun" as A01
  usecase "UC-A02\nMelihat dan Memperbarui Akun" as A02
  usecase "UC-A03\nMenonaktifkan Akun" as A03
  usecase "UC-M01\nLogin (VARK: mahasiswa)" as M01
  usecase "UC-M07\nLogout" as M07
}

Admin --> A01
Admin --> A02
Admin --> A03
Admin --> M01
Admin --> M07

note right of A02
  Precondition: akun target sudah ada.
  Tidak wajib menjalankan UC-A01
  pada sesi yang sama.
end note

note right of A03
  Precondition: akun target ada;
  bukan admin sendiri / admin aktif terakhir.
  Deaktivasi mencabut semua sesi target.
end note

note bottom of A01
  Admin tidak memiliki asosiasi ke
  materi, feedback, submission, atau nilai.
end note

note bottom of M01
  D-18: staf login tanpa memilih VARK.
  VARK hanya untuk mahasiswa.
end note

@enduml
```

## Catatan

- Tidak ada `<<include>>`/`<<extend>>`; prasyarat akun target adalah guard, bukan include.
- Admin hanya mengelola akun (BR-06, FR-034–FR-037).
- Login admin adalah *shared precondition* (lihat OQ-15).
