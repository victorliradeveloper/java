package com.example.downloadmanager.task;

import com.example.downloadmanager.manager.DownloadManager;
import com.example.downloadmanager.model.DownloadStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DownloadTask implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(DownloadTask.class);

    private final String downloadId;
    private final DownloadManager downloadManager;

    public DownloadTask(String downloadId, DownloadManager downloadManager) {
        this.downloadId = downloadId;
        this.downloadManager = downloadManager;
    }

    @Override
    public void run() {
        downloadManager.find(downloadId).ifPresent(this::execute);
    }

    private void execute(DownloadStatus status) {
        log.info("[{}] Starting download: {}", Thread.currentThread().getName(), status.getFileName());
        try {
            for (int progress = 10; progress <= 100; progress += 10) {
                Thread.sleep(500);
                status.updateProgress(progress);
                log.info("[{}] {} -> {}%", Thread.currentThread().getName(), status.getFileName(), progress);
            }
            log.info("[{}] Completed: {}", Thread.currentThread().getName(), status.getFileName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            status.markFailed();
            log.error("[{}] Interrupted: {}", Thread.currentThread().getName(), status.getFileName());
        }
    }
}
