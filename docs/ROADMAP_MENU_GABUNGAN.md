# PulsePro — Roadmap Menu Gabungan

Tanggal: 2026-10-17
Branch: feature/c-masterdata-source

## Tujuan
Menggabungkan TS Gold Overlay dan MiniDataManager ke dalam satu APK, dengan satu floating button C dan satu menu utama.

## Konsep menu
- Satu floating button bulat bertuliskan C.
- Latar emas, simbol emerald; desain simpel, elegan, dan premium.
- Tombol C bisa digeser.
- Saat C diketuk, tampil empat menu:
  1. Master Data — MiniDataManager.
  2. TS — pantauan harga emas yang sudah ada.
  3. News — modul berita, direncanakan menggunakan sumber gemetar.my.id setelah akses dan struktur datanya diperiksa.
  4. Noted — modul catatan.
- Menu memakai tema dark emerald dengan aksen emas.
- Navigasi Android tetap terlihat; aplikasi tidak dibuat fullscreen.
- Pertahankan logika harga beli/jual, perubahan harga, spread, jam, dan koneksi data TS.

## Tahap 1 — Menu gabungan
- [ ] Floating button TS diubah menjadi tombol C.
- [ ] Ketukan C menampilkan menu Master Data, TS, News, dan Noted.
- [ ] Master Data membuka Activity MiniDataManager dalam APK gabungan.
- [ ] TS membuka pantauan harga emas yang sudah ada.
- [ ] News dan Noted memiliki placeholder yang jelas jika belum dikembangkan.
- [ ] Tombol tetap bisa digeser dan tidak mengganggu navigasi Android.
- [ ] Build APK debug berhasil dan hasilnya diperiksa.

## Tahap 2 — Master Data
- [ ] Integrasikan sumber MiniDataManager ke APK gabungan.
- [ ] Pertahankan field No, nomor HP, Nama, Keterangan, dan Email.
- [ ] Kapasitas sasaran minimal 500 record.
- [ ] Pertahankan impor dan ekspor CSV.
- [ ] Rencanakan pengelolaan beberapa dataset/file.
- [ ] Mode GASPOL berfokus pada Keterangan, menyediakan filter KOSONG dan ikon pensil untuk mengedit Keterangan.
- [ ] Filter ENTRY-AN tidak ditampilkan.
- [ ] Mode VIEW dapat melihat dan mengedit No/nomor HP, Nama, dan Email; Keterangan tidak diedit dari mode VIEW.
- [ ] Reset Keterangan wajib meminta konfirmasi dan hanya mengosongkan Keterangan, bukan menghapus record.

## Tahap 3 — News
- [ ] Buat tampilan News dengan tema aplikasi.
- [ ] Periksa sumber gemetar.my.id sebelum integrasi.
- [ ] Jangan menganggap data berhasil diperbarui jika sumber gagal diakses.

## Tahap 4 — Noted
- [ ] Buat modul catatan yang simpel dan konsisten dengan tema aplikasi.
- [ ] Catatan tersimpan lokal dan tetap ada setelah aplikasi ditutup.

## Tahap 5 — Pengujian akhir
- [ ] Uji APK di perangkat Android target, termasuk Vivo jika tersedia.
- [ ] Uji izin overlay, tombol C, menu, dan perpindahan antarfitur.
- [ ] Pastikan navigasi Android tetap terlihat.
- [ ] Verifikasi keamanan dan migrasi data Master Data.

## Perlindungan data lama
Application ID MiniDataManager lama adalah id.dibyo.minidatamanager, sedangkan PulsePro menggunakan id.tsgold.pulsepro. Database privat lama tidak otomatis tersedia bagi APK gabungan.

- Jangan uninstall MiniDataManager lama atau menghapus datanya sebelum backup dan migrasi diverifikasi.
- Gunakan CSV untuk migrasi, kemudian cocokkan jumlah record sebelum dan sesudah impor.
- CSV yang pernah digunakan: Download/MiniDataManager_import_v1.csv.

## Aturan pembaruan roadmap
Setiap kali satu tahap selesai:
1. Perbarui checkbox tahap terkait.
2. Catat file yang berubah dan ringkasan perubahan.
3. Catat hasil build dan pengujian yang benar-benar dilakukan.
4. Catat kendala dan langkah selanjutnya.
5. Commit dan push pembaruan roadmap ke branch kerja.

## Status awal
Konsep sudah ditetapkan. Tahap 1 belum dinyatakan selesai.
