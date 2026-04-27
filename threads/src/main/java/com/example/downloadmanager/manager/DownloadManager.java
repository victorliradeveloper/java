package com.example.downloadmanager.manager;

import com.example.downloadmanager.model.DownloadStatus;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DownloadManager {

    private final ConcurrentHashMap<String, DownloadStatus> downloads = new ConcurrentHashMap<>();

    public void register(DownloadStatus status) {
        downloads.put(status.getId(), status);
    }

    public Optional<DownloadStatus> find(String id) {
        return Optional.ofNullable(downloads.get(id));
    }

    public Collection<DownloadStatus> findAll() {
        return downloads.values();
    }
}
