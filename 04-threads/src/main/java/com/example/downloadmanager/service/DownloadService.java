package com.example.downloadmanager.service;

import com.example.downloadmanager.manager.DownloadManager;
import com.example.downloadmanager.model.DownloadStatus;
import com.example.downloadmanager.task.DownloadTask;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

@Service
public class DownloadService {

    private final ExecutorService executor;
    private final DownloadManager downloadManager;

    public DownloadService(ExecutorService executor, DownloadManager downloadManager) {
        this.executor = executor;
        this.downloadManager = downloadManager;
    }

    public DownloadStatus start(String fileName) {
        String id = UUID.randomUUID().toString();
        DownloadStatus status = new DownloadStatus(id, fileName);

        downloadManager.register(status);
        executor.submit(new DownloadTask(id, downloadManager));

        return status;
    }

    public Optional<DownloadStatus> findById(String id) {
        return downloadManager.find(id);
    }

    public Collection<DownloadStatus> findAll() {
        return downloadManager.findAll();
    }
}
