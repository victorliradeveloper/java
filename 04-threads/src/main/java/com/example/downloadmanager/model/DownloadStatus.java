package com.example.downloadmanager.model;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class DownloadStatus {

    private final String id;
    private final String fileName;
    private final LocalDateTime startedAt;
    private final AtomicInteger progress;
    private final AtomicReference<DownloadStatusEnum> status;

    public DownloadStatus(String id, String fileName) {
        this.id = id;
        this.fileName = fileName;
        this.startedAt = LocalDateTime.now();
        this.progress = new AtomicInteger(0);
        this.status = new AtomicReference<>(DownloadStatusEnum.STARTED);
    }

    public void updateProgress(int value) {
        progress.set(value);
        if (value > 0 && value < 100) {
            status.set(DownloadStatusEnum.RUNNING);
        } else if (value >= 100) {
            status.set(DownloadStatusEnum.COMPLETED);
        }
    }

    public void markFailed() {
        status.set(DownloadStatusEnum.FAILED);
    }

    public String getId() { return id; }
    public String getFileName() { return fileName; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public int getProgress() { return progress.get(); }
    public DownloadStatusEnum getStatus() { return status.get(); }
}
