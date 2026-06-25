---
trigger: always_on
---

AI Agent Coding Rules & Standards
Core Philosophy
Write Clean, Self-Documenting Code: Prioritaskan keterbacaan. Jika butuh komentar panjang, berarti kodenya terlalu kompleks.

Don't Just Solve, Optimize: Jangan hanya memberikan solusi yang "work", tapi berikan yang paling efisien secara performa dan memori.

Modular & Scalable: Gunakan prinsip SOLID dan DRY. Pisahkan logic bisnis dari komponen UI atau handler API.

Strict MVVM/MVC: Pisahkan UI (.fxml), Styling (.css), dan Logic (Controller.java). Jangan menulis kode layout langsung di dalam Java kecuali sangat diperlukan untuk komponen dinamis.

Controller Lean: Controller hanya boleh menangani event handling dan pemanggilan service. Business logic (analisis log, parsing) harus berada di layer Service atau Util.

Dependency Injection: Gunakan pendekatan singleton atau DI sederhana agar instance Service dapat digunakan bersama di berbagai Controller.

Don't Block the UI Thread: Semua proses berat (membaca file log besar, regex parsing, network request) WAJIB dijalankan di background thread (Task<V> atau Service<V>).

UI Updates: Gunakan Platform.runLater() hanya untuk update komponen UI dari background thread. Pastikan tidak terjadi nested runLater.

Virtualization: Untuk menampilkan ribuan baris log, gunakan ListView atau TableView dengan Virtualization yang aktif. Jangan pernah memasukkan 10.000+ item ke dalam VBox secara manual.