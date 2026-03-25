package com.geo.analytics.application.service;

import com.geo.analytics.application.dto.KeywordRegistrationRequest;
import com.geo.analytics.application.dto.KeywordRegistrationResult;
import com.geo.analytics.application.dto.SelectedKeyword;
import com.geo.analytics.domain.entity.ProjectEntity;
import com.geo.analytics.domain.entity.ProjectKeywordEntity;
import com.geo.analytics.domain.enums.AnalysisPriority;
import com.geo.analytics.domain.enums.PreferredEngine;
import com.geo.analytics.infrastructure.repository.ProjectKeywordRepository;
import com.geo.analytics.infrastructure.repository.ProjectRepository;
import com.geo.analytics.infrastructure.tenant.DefaultTenantIds;
import com.geo.analytics.infrastructure.tenant.TenantContext;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class KeywordRoutingService {
    private final ProjectRepository projectRepository;
    private final ProjectKeywordRepository projectKeywordRepository;

    public KeywordRoutingService(ProjectRepository projectRepository, ProjectKeywordRepository projectKeywordRepository) {
        this.projectRepository = projectRepository;
        this.projectKeywordRepository = projectKeywordRepository;
    }

    @Transactional
    public KeywordRegistrationResult registerKeywords(UUID pathProjectId, KeywordRegistrationRequest keywordRegistrationRequest) {
        if (!pathProjectId.equals(keywordRegistrationRequest.projectId())) {
            throw new IllegalArgumentException("projectId mismatch");
        }
        UUID projectId = keywordRegistrationRequest.projectId();
        return TenantContext.executeWithTenant(DefaultTenantIds.WORKSPACE_ID, () -> {
            ProjectEntity projectEntity = projectRepository.findById(projectId)
                .orElseThrow(() -> new EntityNotFoundException("Project not found: " + projectId));
            UUID workspaceId = projectEntity.getWorkspaceId();
            return TenantContext.executeWithTenant(workspaceId, () -> persistKeywords(projectEntity, keywordRegistrationRequest.keywords()));
        });
    }

    private KeywordRegistrationResult persistKeywords(ProjectEntity projectEntity, List<SelectedKeyword> selectedKeywords) {
        UUID projectId = projectEntity.getId();
        UUID workspaceId = projectEntity.getWorkspaceId();
        LinkedHashMap<String, SelectedKeyword> dedup = new LinkedHashMap<>();
        for (SelectedKeyword selectedKeyword : selectedKeywords) {
            String t = selectedKeyword.text().trim();
            if (t.isEmpty()) {
                continue;
            }
            dedup.put(t, selectedKeyword);
        }
        if (dedup.isEmpty()) {
            return new KeywordRegistrationResult(0, 0);
        }
        List<String> texts = new ArrayList<>(dedup.keySet());
        List<ProjectKeywordEntity> existing = projectKeywordRepository.findAllByProjectIdAndKeywordTextIn(projectId, texts);
        Set<String> have = existing.stream().map(ProjectKeywordEntity::getKeywordText).collect(Collectors.toSet());
        List<ProjectKeywordEntity> toSave = new ArrayList<>();
        int skipped = 0;
        for (Map.Entry<String, SelectedKeyword> entry : dedup.entrySet()) {
            String text = entry.getKey();
            if (have.contains(text)) {
                skipped++;
                continue;
            }
            Routed routed = route(entry.getValue().categoryName());
            ProjectKeywordEntity projectKeywordEntity = new ProjectKeywordEntity();
            projectKeywordEntity.setWorkspaceId(workspaceId);
            projectKeywordEntity.setProjectId(projectId);
            projectKeywordEntity.setKeywordText(text);
            projectKeywordEntity.setAnalysisPriority(routed.analysisPriority());
            projectKeywordEntity.setPreferredEngine(routed.preferredEngine());
            toSave.add(projectKeywordEntity);
        }
        if (!toSave.isEmpty()) {
            projectKeywordRepository.saveAll(toSave);
        }
        return new KeywordRegistrationResult(toSave.size(), skipped);
    }

    private static Routed route(String categoryName) {
        String c = categoryName != null ? categoryName.strip() : "";
        if ("比較・検討".equals(c) || "悩み・課題解決".equals(c)) {
            return new Routed(AnalysisPriority.HIGH, PreferredEngine.SERP_API);
        }
        if ("業界・一般".equals(c) || "潜在層".equals(c)) {
            return new Routed(AnalysisPriority.NORMAL, PreferredEngine.GEMINI_BATCH);
        }
        return new Routed(AnalysisPriority.NORMAL, PreferredEngine.GEMINI_BATCH);
    }

    private record Routed(AnalysisPriority analysisPriority, PreferredEngine preferredEngine) {
    }
}
