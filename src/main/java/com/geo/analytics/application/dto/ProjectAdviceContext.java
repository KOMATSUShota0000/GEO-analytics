package com.geo.analytics.application.dto;

import com.geo.analytics.domain.enums.IndustryType;
import java.util.UUID;

/**
 * AI 議論駆動アドバイス生成 ({@code DebateAdviceGeneratorService}) に渡すための
 * プロジェクト情報の軽量スナップショット。
 *
 * <p>{@code ProjectEntity}（JPA）を直接渡すと、JDBC ベースの呼び出し元
 * （{@code GapAnalysisService} / {@code BatchPersistenceService}）に JPA 依存を持ち込んでしまうため、
 * 必要なフィールドのみを切り出した record で受け渡す。
 *
 * <p>{@code projectId} / {@code workspaceId} / {@code organizationId} は Sprint 2 の
 * Pro/Expert 短縮版議論におけるチケット消費（{@code CreditVaultService}）と、
 * 非同期 gap analysis スレッドでのテナントコンテキスト確立に用いる。
 * これらが null の場合は課金を伴う議論起動を行わず Free パス相当で動作する。
 */
public record ProjectAdviceContext(
        IndustryType industryType,
        String targetAudience,
        String extractedStrengths,
        UUID projectId,
        UUID workspaceId,
        UUID organizationId) {

    public ProjectAdviceContext {
        if (industryType == null) {
            industryType = IndustryType.OTHER;
        }
    }

    /** 課金識別子を持たない後方互換コンストラクタ（Free パス相当・テスト用）。 */
    public ProjectAdviceContext(
            IndustryType industryType, String targetAudience, String extractedStrengths) {
        this(industryType, targetAudience, extractedStrengths, null, null, null);
    }

    /** Pro/Expert の課金を伴う議論起動が可能か（テナント識別子が揃っているか）。 */
    public boolean hasBillingIdentity() {
        return projectId != null && workspaceId != null && organizationId != null;
    }
}
