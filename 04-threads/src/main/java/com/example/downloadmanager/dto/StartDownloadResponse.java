package com.example.downloadmanager.dto;

import com.example.downloadmanager.model.DownloadStatusEnum;

public record StartDownloadResponse(
        String id,
        String fileName,
        DownloadStatusEnum status
) {}
