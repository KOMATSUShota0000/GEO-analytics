package com.geo.analytics.domain.entity;

import com.geo.analytics.domain.enums.RagDomainRuleKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "rag_domain_rules")
public class RagDomainRuleEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "host_suffix", nullable = false, length = 255, unique = true)
    private String hostSuffix;
    @Enumerated(EnumType.STRING)
    @Column(name = "rule_kind", nullable = false, length = 32)
    private RagDomainRuleKind ruleKind;
    @Column(name = "trust_boost")
    private Double trustBoost;
    @Column(name = "active", nullable = false)
    private boolean active = true;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getHostSuffix() {
        return hostSuffix;
    }

    public void setHostSuffix(String hostSuffix) {
        this.hostSuffix = hostSuffix;
    }

    public RagDomainRuleKind getRuleKind() {
        return ruleKind;
    }

    public void setRuleKind(RagDomainRuleKind ruleKind) {
        this.ruleKind = ruleKind;
    }

    public Double getTrustBoost() {
        return trustBoost;
    }

    public void setTrustBoost(Double trustBoost) {
        this.trustBoost = trustBoost;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
