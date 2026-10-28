# Catatan Modifikasi (Fork brahmasyahdan/droiddesk)

Fork ini berbasis [orailnoor/DroidDesk](https://github.com/orailnoor/DroidDesk) v1.3.1
(commit `a410944`). Saya mencoba memodifikasi beberapa bagian, terutama supaya DroidDesk
lebih nyaman dipakai di HP Android standar dengan **RAM 4 GB tanpa keyboard dan mouse fisik**.

Semua perubahan diuji di **Samsung Galaxy A04s** (Android 14, Exynos 850 / GPU Mali,
RAM 3,7 GB, mode non-root, render software llvmpipe).

Sesuai GPL-3.0, berkas yang diubah tetap memakai lisensi aslinya.

---

## 1. Low memory mode (untuk HP RAM ≤ 4 GB)

Toggle baru di **Settings > Low memory mode**. Otomatis aktif di HP dengan RAM di bawah
sekitar 4,5 GB, dan berlaku saat desktop dijalankan berikutnya.

- Mematikan compositor XFCE (efek transparansi), yang menyimpan salinan setiap jendela di memori.
- Mematikan thumbnail di file manager Thunar.
- Menonaktifkan `xfce4-power-manager` + `upowerd`. Di Android keduanya tidak berfungsi, dan
  `upowerd` gagal membaca baterai setiap 5 detik.
- Sinkronisasi clipboard tiap 1,5 detik (sebelumnya 0,8 detik).
- Mode root (chroot): `MALLOC_ARENA_MAX=2` untuk app Ubuntu.
- Saat dimatikan, setelan XFCE dikembalikan ke default **satu kali** saja, jadi setelan yang
  diubah manual tidak ditimpa.

Efek samping: latar dock jadi hitam, karena transparansi butuh compositor.

Kode: `runtime/LowMemoryProfile.kt` (baru), dipanggil dari `LinuxRuntime.kt` dan `ChrootRuntime.kt`.

## 2. Peringatan memori hampir penuh

Selama desktop terbuka, app memeriksa memori setiap 15 detik dan menampilkan peringatan
sebelum Android mematikan seluruh sesi Linux (`view/DesktopActivity.kt`).

## 3. Tombol mouse virtual (Left / Scroll / Right)

Untuk HP tanpa mouse. Aktifkan lewat **☰ > ikon klik**. Pil melayang berisi:

| Tombol | Fungsi |
|---|---|
| **Left / Right** | Klik di posisi kursor. Bisa ditahan: tahan Left lalu geser jari lain = drag, seperti touchpad laptop |
| **⇕** | Geser naik/turun = scroll, tap = klik tengah |
| **⋮⋮** | Pegangan untuk memindahkan pil |

Setelan tersimpan. Kalau mode input masih "Direct touch", app otomatis pindah ke Trackpad.

Kode: `view/MouseButtonsBar.kt` (baru), `x11/X11InputController.kt`.

## 4. Scroll satu jari di mode Touchscreen

Sebelumnya, di mode **Touchscreen** geser satu jari tidak melakukan apa-apa. Sekarang geser
satu jari = scroll ke atas, bawah, kiri, dan kanan, seperti di HP. Tekan lama lalu geser tetap
untuk drag/seleksi, dan tap tetap klik.

Kode: `java/com/termux/x11/input/TouchInputHandler.java`.

## 5. Jendela otomatis pas dengan layar (potret dan landscape)

- Script `devilspie2` sekarang juga bereaksi saat judul jendela berubah dan saat jendela
  difokus. Jendela yang lepas dari maximize tepat setelah dibuka di-maximize sekali lagi, dan
  jendela yang lebih besar dari layar (misalnya setelah HP diputar) di-maximize.
- Jendela yang sudah maximize tidak lagi diubah ukurannya saat dibuka, karena sebelumnya hal itu
  justru membatalkan maximize.
- Bug `devilspie2` berjalan **dua kali** diperbaiki: paketnya punya autostart sendiri di samping
  autostart DroidDesk.

Kode: `runtime/XfceMobileProfile.kt`.

## 6. Firefox tampil penuh seperti di desktop

Firefox desktop tidak mau lebih sempit dari sekitar 725 px CSS, sehingga di layar potret
jendelanya terpotong dan tidak bisa di-maximize.

- Skala Firefox diset ke `layout.css.devPixelsPerPx = 0.6` lewat pref default
  (`defaults/pref/droiddesk.js`). Jendela muat di potret, dan banyak situs tampil versi desktop.
  Nilainya bisa diganti di `about:config`; nilai di atas sekitar 0,63 membuat Firefox kembali
  tidak muat di potret.
- Firefox selalu dibuka maximize di setiap start desktop (mode non-root).

Kode: `runtime/FirefoxDefaults.kt` (baru).

## 7. Perbaikan lain

- **Layar "Set Up" tidak lagi muncul sesaat** saat app dibuka. Selama status runtime belum
  termuat, app menampilkan logo dengan loading (`lib/main.dart`, `lib/state/app_state.dart`).
- **Isi clipboard tidak lagi tercatat di logcat** pada mode root (`RootShell.kt`), dan celah
  penyisipan perintah di `RootShell.execInChroot` ditutup.
- `usesCleartextTraffic` dimatikan di build rilis; build debug masih mengizinkannya untuk tes updater.
- Project bisa dibangun dengan Flutter versi lama sesuai `pubspec.yaml`: `onReorderItem` diganti
  `onReorder` di `desktop_tools_screen.dart`.
- Kode mati dihapus: stub compositor Wayland (`CompositorService.kt`, `cpp/`), `app/test_ffi.dart`,
  dan `fetch_deps.sh`.
- Panduan untuk HP 4 GB ditambahkan di `APP_TROUBLESHOOTING.md`, termasuk cara mematikan
  *phantom process killer* Android 12+.

## 8. Test

- `app/test/update_service_test.dart`: pengecekan versi update.
- `app/android/app/src/test/.../LowMemoryProfileTest.kt`: menjalankan script shell yang
  dihasilkan Low memory mode di folder sementara.

```bash
cd app && flutter analyze && flutter test
cd android && ./gradlew :app:testDebugUnitTest
```

## Belum selesai / perlu dicek

- Setelah HP diputar landscape lalu kembali ke potret, isi halaman Firefox sempat terlihat
  tidak digambar ulang. Belum dipastikan apakah ini bug.
- Mode root (chroot): Firefox belum otomatis dibuka maximize (skala 0,6 sudah diterapkan).
- Pengujian dilakukan dengan build debug. Pemakaian RAM build rilis belum diukur.
- Updater di dalam app masih memeriksa rilis repo asli (`orailnoor/DroidDesk`, di
  `lib/services/update_service.dart`). APK dari fork ini ditandatangani dengan kunci berbeda,
  jadi update dari repo asli akan ditolak, dan sebaliknya.
