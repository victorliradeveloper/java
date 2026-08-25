package com.example.downloadmanager.controller;

import com.example.downloadmanager.dto.DownloadStatusResponse;
import com.example.downloadmanager.dto.StartDownloadRequest;
import com.example.downloadmanager.dto.StartDownloadResponse;
import com.example.downloadmanager.model.DownloadStatus;
import com.example.downloadmanager.service.DownloadService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/downloads")
public class DownloadController {

    private final DownloadService downloadService;

    public DownloadController(DownloadService downloadService) {
        this.downloadService = downloadService;
    }

    @PostMapping
    public ResponseEntity<StartDownloadResponse> start(@RequestBody StartDownloadRequest request) {
        DownloadStatus status = downloadService.start(request.fileName());
        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(new StartDownloadResponse(status.getId(), status.getFileName(), status.getStatus()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DownloadStatusResponse> getById(@PathVariable String id) {
        return downloadService.findById(id)
                .map(DownloadStatusResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<List<DownloadStatusResponse>> getAll() {
        List<DownloadStatusResponse> responses = downloadService.findAll()
                .stream()
                .map(DownloadStatusResponse::from)
                .toList();
        return ResponseEntity.ok(responses);
    }
}
