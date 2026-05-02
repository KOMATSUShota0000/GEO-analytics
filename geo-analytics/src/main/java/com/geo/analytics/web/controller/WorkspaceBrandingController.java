package com.geo.analytics.web.controller;

import com.geo.analytics.application.service.BrandingService;
import com.geo.analytics.web.dto.WorkspaceBrandingResponse;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workspaces/current/branding")
public class WorkspaceBrandingController {

    private final BrandingService brandingService;

    public WorkspaceBrandingController(BrandingService brandingService) {
        this.brandingService = brandingService;
    }

    @GetMapping
    @PreAuthorize("@tenantAccessEvaluator.canReadWorkspaceBranding(authentication)")
    public WorkspaceBrandingResponse getBranding() {
        return brandingService.getBranding();
    }

    @GetMapping("/logo")
    @PreAuthorize("@tenantAccessEvaluator.canReadWorkspaceBranding(authentication)")
    public ResponseEntity<Resource> getLogo() throws IOException {
        try {
            Resource resource = brandingService.loadLogoResource();
            Path path = resource.getFile().toPath();
            MediaType mediaType = resolveImageMediaType(path);
            return ResponseEntity.ok().contentType(mediaType).body(resource);
        } catch (FileNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    private static MediaType resolveImageMediaType(Path path) throws IOException {
        String probed = Files.probeContentType(path);
        if (probed != null && !probed.isBlank()) {
            try {
                return MediaType.parseMediaType(probed);
            } catch (IllegalArgumentException ex) {
            }
        }
        String fileName = path.getFileName().toString().toLowerCase();
        MediaType fallback = fallbackByExtension(fileName);
        if (fallback != null) {
            return fallback;
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    private static MediaType fallbackByExtension(String fileNameLower) {
        int dot = fileNameLower.lastIndexOf('.');
        if (dot < 0 || dot == fileNameLower.length() - 1) {
            return null;
        }
        return switch (fileNameLower.substring(dot + 1)) {
            case "png" -> MediaType.IMAGE_PNG;
            case "jpg", "jpeg" -> MediaType.IMAGE_JPEG;
            case "gif" -> MediaType.IMAGE_GIF;
            case "webp" -> MediaType.parseMediaType("image/webp");
            case "svg" -> MediaType.parseMediaType("image/svg+xml");
            case "bmp" -> MediaType.parseMediaType("image/bmp");
            case "ico" -> MediaType.parseMediaType("image/x-icon");
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }
}
