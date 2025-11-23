package com.seeloggyplus.service;

public interface ProgressCallback {
    /**
     * Dipanggil saat ada kemajuan proses (download/parsing)
     * @param progress Persentase (0.0 sampai 1.0)
     * @param bytesProcessed Jumlah byte yang sudah diproses
     * @param totalBytes Total ukuran file (byte)
     */
    void onProgress(double progress, long bytesProcessed, long totalBytes);

    /**
     * Dipanggil saat proses selesai
     * @param totalItems Total item/baris yang diproses
     */
    void onComplete(long totalItems);
}
