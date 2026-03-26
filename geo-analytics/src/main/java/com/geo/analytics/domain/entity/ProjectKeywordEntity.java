package com.geo.analytics.domain.entity;
import com.geo.analytics.domain.enums.AnalysisPriority;
import com.geo.analytics.domain.enums.PreferredEngine;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
@Entity
@Table(
    name = "project_keywords",
    uniqueConstraints = @UniqueConstraint(name = "uk_project_keywords_project_text", columnNames = { "project_id", "keyword_text" }))
public class ProjectKeywordEntity extends BaseTenantEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "project_id", nullable = false)
    private UUID projectId;
    @Column(name = "keyword_text", nullable = false, columnDefinition = "text")
    private String keywordText;
    @Enumerated(EnumType.STRING)
    @Column(name = "analysis_priority", nullable = false, length = 16)
    private AnalysisPriority analysisPriority;
    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_engine", nullable = false, length = 32)
    private PreferredEngine preferredEngine;
    public ProjectKeywordEntity() {
    }
    public UUID getId() {
        return id;
    }
    public void setId(UUID id) {
        this.id = id;
    }
    public UUID getProjectId() {
        return projectId;
    }
    public void setProjectId(UUID projectId) {
        this.projectId = projectId;
    }
    public String getKeywordText() {
        return keywordText;
    }
    public void setKeywordText(String keywordText) {
        this.keywordText = keywordText;
    }
    public AnalysisPriority getAnalysisPriority() {
        return analysisPriority;
    }
    public void setAnalysisPriority(AnalysisPriority analysisPriority) {
        this.analysisPriority = analysisPriority;
    }
    public PreferredEngine getPreferredEngine() {
        return preferredEngine;
    }
    public void setPreferredEngine(PreferredEngine preferredEngine) {
        this.preferredEngine = preferredEngine;
    }
}
