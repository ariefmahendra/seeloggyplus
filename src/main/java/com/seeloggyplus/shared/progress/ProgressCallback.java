package com.seeloggyplus.shared.progress;
public interface ProgressCallback {

    void onProgress(double progress, long bytesProcessed, long totalBytes);

    void onComplete(long totalItems);
}
