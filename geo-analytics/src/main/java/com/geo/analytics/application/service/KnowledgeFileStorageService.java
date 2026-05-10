package com.geo.analytics.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class KnowledgeFileStorageService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeFileStorageService.class);
    private static final String RELATIVE_ROOT = "geo-analytics-knowledge-uploads";

    public List<Path> storeUploadedFiles(UUID jobId, MultipartFile[] files) {
        if (files == null || files.length == 0) {
            return List.of();
        }
        Path dir =
                Paths.get(System.getProperty("java.io.tmpdir"))
                        .resolve(RELATIVE_ROOT)
                        .resolve(jobId.toString());
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        List<Path> stored = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            String safeName = sanitizeFileName(file.getOriginalFilename());
            Path target = dir.resolve(UUID.randomUUID() + "_" + safeName);
            try {
                file.transferTo(target);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            stored.add(target.toAbsolutePath().normalize());
        }
        return List.copyOf(stored);
    }

    public void deleteStagingDirectory(UUID jobId) {
        Path root =
                Paths.get(System.getProperty("java.io.tmpdir")).resolve(RELATIVE_ROOT).resolve(jobId.toString());
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(
                            path -> {
                                try {
                                    Files.deleteIfExists(path);
                                } catch (IOException e) {
                                    log.warn("Failed to delete staging path={}", path.toAbsolutePath(), e);
                                }
                            });
        } catch (IOException e) {
            log.warn("Failed to walk staging directory jobId={}", jobId, e);
        }
    }

    private static String sanitizeFileName(String original) {
        if (original == null || original.isBlank()) {
            return "upload.bin";
        }
        String base = Paths.get(original).getFileName().toString();
        base = base.replace('\u0000', '_');
        base = base.replace("..", "_");
        if (base.isBlank()) {
            return "upload.bin";
        }
        if (base.length() > 255) {
            return base.substring(0, 255);
        }
        return base;
    }
}
