## Temuan Bug ketika testing aplikasi di hp Xiaomi
### Dosen
1. ketika ada mahasiswa yang sudah mengumpulkan jawaban studi kasus di pilihan kinesthetic dosen belum bisa melihat jawaban dari mahasiswa tersebut, padahal sistem sudah menampilkan ada 1 pengumpulan di tab penilaian
2. ketika dosen ingin mengelola materi pada topik yang sudah dibuat baik sudah terbit maupun masih dalam draf, sistem menolak dengan alasan "tidak dapat dimuat, pilih VARK terlebih dahulu", yang seharusnya sistem bisa menyediakan opsi ini untuk dosen mengelola materi yang sudah terbit maupun yang masih dalam draft

### Sistem
1. pada kolom NIM di login, buat akun (khusus admin), maupun input apapun itu jika berkaitan dengan NIM/NIP/NIDN minimal karakter yang diinput adalah 10 (sesuai dengan panjang digit dosen di indonesia yaitu 10), untuk maksimal 11 karakter, ini menyesuaikan panjang nim di universitah muhammadiyah yogyakarta. jadi intinya kolom NIM/NIP/NIDN hanya di rentang 10-11
2. jadi semua login menggunakan NIM/NIP/NIDN (kecuali login admin menggunakan admin dan password), selain login admin semua menggunakan NIM/NIP/NIDN