# TS Gold Pulse Pro — V19

## Status
- Version code: 19
- Version name: 19.0
- Build: Gradle release — BUILD SUCCESSFUL
- APK: TS_Gold_Pulse_Pro_V19.apk
- Signing: Android Debug keystore
- Catatan: APK sudah ditandatangani dan sertifikat berhasil diverifikasi.
- Uji instalasi dan tampilan di perangkat tetap perlu dilakukan.

## Perubahan V19
- Logo floating button berubah dari TS menjadi CH.
- Tulisan logo tetap emas.
- Latar emerald dan outline tetap menggunakan warna V18.
- Ukuran teks harga beli/jual, delta, jam, dan spread disesuaikan.
- Lebar overlay disesuaikan menjadi 320dp.
- Tinggi area harga disesuaikan dengan perubahan ukuran teks.

## Prinsip perubahan
- Jangan mengubah alur Pusher atau sumber harga tanpa kebutuhan.
- Pertahankan indikator kenaikan/penurunan dan perilaku floating button.
- Pertahankan desain premium emerald-gold yang sudah ada.
- Setiap rilis menggunakan versionCode/versionName dan nama APK berurutan.
- Jangan commit keystore, password, APK hasil build, atau file backup sementara.

## Build di Termux
```bash
cd ~/PulsePro
gradle assembleRelease
```

Jika hasil build berupa app-release-unsigned.apk, tandatangani menggunakan
keystore yang sama seperti rilis sebelumnya agar pembaruan dapat dipasang
di atas versi lama. Jangan mengunggah keystore ke repository.

## Rilis berikutnya
Sebelum upgrade:
1. Sinkronkan repository.
2. Periksa versi dan perubahan source terakhir.
3. Buat patch sekecil mungkin.
4. Naikkan versi.
5. Build, sign, dan verifikasi APK.
6. Catat perubahan rilis di file ini.
