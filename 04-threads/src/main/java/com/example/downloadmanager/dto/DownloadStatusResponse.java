package com.example.downloadmanager.dto;

import com.example.downloadmanager.model.DownloadStatus;
import com.example.downloadmanager.model.DownloadStatusEnum;

import java.time.LocalDateTime;

public record DownloadStatusResponse(
        String id,
        String fileName,
        int progress,
        DownloadStatusEnum status,
        LocalDateTime startedAt
) {
    public static DownloadStatusResponse from(DownloadStatus ds) {
        return new DownloadStatusResponse(
                ds.getId(),
                ds.getFileName(),
                ds.getProgress(),
                ds.getStatus(),
                ds.getStartedAt()
        );
    }
}
