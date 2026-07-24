package com.zxylearn.build_guard_server.controller;

import com.zxylearn.build_guard_server.entity.FileResource;
import com.zxylearn.build_guard_server.service.FileStorageService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/files")
public class FileController {
    private final FileStorageService fileStorageService;

    public FileController(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Resource> getFile(@PathVariable long id) {
        FileResource file = fileStorageService.findById(id);
        Resource resource = fileStorageService.localResource(file);
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (file.getContentType() != null && !file.getContentType().isBlank()) {
            mediaType = MediaType.parseMediaType(file.getContentType());
        }
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=3600")
                .body(resource);
    }
}
