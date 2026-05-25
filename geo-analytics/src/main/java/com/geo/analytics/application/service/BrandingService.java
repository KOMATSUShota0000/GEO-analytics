package com.geo.analytics.application.service;

import com.geo.analytics.domain.entity.OrganizationEntity;
import com.geo.analytics.domain.enums.SubscriptionPlan;
import com.geo.analytics.infrastructure.config.AppProperties;
import com.geo.analytics.infrastructure.repository.OrganizationRepository;
import com.geo.analytics.infrastructure.tenant.TenantContextHolder;
import com.geo.analytics.web.dto.WorkspaceBrandingResponse;
import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BrandingService {

    private static final String LOGO_URL = "/api/v1/workspaces/current/branding/logo";

    private final OrganizationRepository organizationRepository;
    private final AppProperties appProperties;
    private final WorkspacePlanResolver workspacePlanResolver;

    public BrandingService(
            OrganizationRepository organizationRepository,
            AppProperties appProperties,
            WorkspacePlanResolver workspacePlanResolver) {
        this.organizationRepository = organizationRepository;
        this.appProperties = appProperties;
        this.workspacePlanResolver = workspacePlanResolver;
    }

    @Transactional(readOnly = true)
    public WorkspaceBrandingResponse getBranding() {
        UUID orgId = TenantContextHolder.getOrganizationId()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST));
        OrganizationEntity org = organizationRepository
                .findByIdAndDeletedAtIsNull(orgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        String logoUrl = logoEntitled() ? LOGO_URL : "";
        return new WorkspaceBrandingResponse(org.getToolName(), org.getBrandColor(), logoUrl);
    }

    @Transactional(readOnly = true)
    public Resource loadLogoResource() throws FileNotFoundException {
        if (!logoEntitled()) {
            throw new FileNotFoundException("logo");
        }
        UUID orgId = TenantContextHolder.getOrganizationId()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST));
        OrganizationEntity org = organizationRepository
                .findByIdAndDeletedAtIsNull(orgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        String logoFilePath = org.getLogoFilePath();
        if (logoFilePath == null || logoFilePath.isBlank()) {
            throw new FileNotFoundException("logo");
        }
        Path resolved = validateAndResolveRelativeLogoPath(logoFilePath);
        if (!java.nio.file.Files.isRegularFile(resolved)) {
            throw new FileNotFoundException(resolved.toString());
        }
        return new PathResource(resolved);
    }

    private boolean logoEntitled() {
        return TenantContextHolder.getTenantId()
                .map(workspacePlanResolver::resolvePlan)
                .map(SubscriptionPlan::usesProTierFeatures)
                .orElse(false);
    }

    private Path normalizedStorageRoot() {
        String root = appProperties.getBranding().getStorageRoot();
        return Paths.get(root).toAbsolutePath().normalize();
    }

    private Path validateAndResolveRelativeLogoPath(String logoFilePath) {
        String trimmed = logoFilePath.trim();
        if (trimmed.contains("..")) {
            throw new SecurityException();
        }
        if (!trimmed.isEmpty() && (trimmed.charAt(0) == '/' || trimmed.charAt(0) == '\\')) {
            throw new SecurityException();
        }
        for (int i = 0; i < trimmed.length(); i++) {
            if (trimmed.charAt(i) == '\u0000') {
                throw new SecurityException();
            }
        }
        Path root = normalizedStorageRoot();
        Path relative = Paths.get(trimmed);
        if (relative.isAbsolute()) {
            throw new SecurityException();
        }
        Path normalizedRel = relative.normalize();
        if (normalizedRel.getNameCount() == 0) {
            throw new SecurityException();
        }
        for (Path name : normalizedRel) {
            String seg = name.toString();
            if (seg.isEmpty() || seg.equals(".") || seg.equals("..")) {
                throw new SecurityException();
            }
            if (seg.indexOf('/') >= 0 || seg.indexOf('\\') >= 0) {
                throw new SecurityException();
            }
        }
        Path resolved = root.resolve(normalizedRel).normalize();
        if (!resolved.startsWith(root)) {
            throw new SecurityException();
        }
        return resolved;
    }
}
