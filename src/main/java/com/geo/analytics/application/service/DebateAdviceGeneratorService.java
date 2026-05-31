package com.geo.analytics.application.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geo.analytics.application.dto.ProjectAdviceContext;
import com.geo.analytics.application.dto.StrategyInsight;
import com.geo.analytics.domain.ai.DebatePersona;
import com.geo.analytics.domain.entity.AuditHistoryEntity;
import com.geo.analytics.domain.enums.IndustryType;
import com.geo.analytics.domain.enums.SubscriptionPlan;
import com.geo.analytics.domain.prompt.DebatePersonaSystemPrompts;
import com.geo.analytics.infrastructure.config.AiConfig;
import com.geo.analytics.infrastructure.tenant.TenantContextHolder;
import com.geo.analytics.infrastructure.tenant.TenantIdentity;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import java.lang.ScopedValue;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * ジョブ全体アドバイスを 4 ペルソナ議論の知見をベースに AI で生成するサービス。
 *
 * <p>本サービスは {@link StrategyInsightService#rollupJob(List)} のテンプレ実装を
 * 置き換える形で呼び出される。LLM 呼び出しが失敗した場合は呼び出し元
 * （StrategyInsightService）がテンプレフォールバックに切り替える。
 *
 * <p>プラン別動作（仕様書: .cursor/plans/2026-05-30-debate-driven-advice.md, ADR-011/012）:
 * <ul>
 *   <li>STANDARD: オンボーディング議論の結果（ProjectEntity に保存済み）をプロンプトに含め DIRECTOR LLM 1 回呼び出し</li>
 *   <li>PRO / EXPERT: 解析ごとに <b>短縮版議論（2 ターン上限）</b> を起動し、その結論を DIRECTOR プロンプトに注入。
 *       議論起動 1 回につき 0.2 チケット（{@value #DEBATE_CREDIT} 単位）を消費する。
 *       議論失敗時はチケットを全額返金し、Free パス（議論なし単発）へフォールバックする</li>
 * </ul>
 */
@Service
public class DebateAdviceGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(DebateAdviceGeneratorService.class);
    private static final Logger SECURITY_AUDIT = LoggerFactory.getLogger("SECURITY_AUDIT");

    private static final int DIAGNOSTIC_MAX_CHARS = 300;
    private static final int ACTION_MAX_CHARS = 60;
    private static final int RECOMMENDED_ACTIONS_COUNT = 3;

    /**
     * 短縮版議論のターン上限。<b>コスト試算（2026-05-30）の生命線</b>であり、
     * これを超えると履歴トークンが肥大して限界利益率 86% を割る恐れがあるためハード固定する。
     */
    static final int SHORT_DEBATE_TURNS = 2;

    /** Pro/Expert 議論起動 1 回あたりのチケット消費（0.2 チケット = 200 単位。1 解析 = 1,000 単位スケール）。 */
    public static final long DEBATE_CREDIT = 200L;

    private final ChatLanguageModel directorChatModel;
    private final ChatLanguageModel analystChatModel;
    private final ChatLanguageModel innovatorChatModel;
    private final ChatLanguageModel skepticChatModel;
    private final ObjectMapper objectMapper;
    private final StrategyInsightService strategyInsightService;
    private final CreditVaultService creditVaultService;

    public DebateAdviceGeneratorService(
            @Qualifier(AiConfig.GEMINI_DEBATE_DIRECTOR) ChatLanguageModel directorChatModel,
            @Qualifier(AiConfig.GEMINI_DEBATE_ANALYST) ChatLanguageModel analystChatModel,
            @Qualifier(AiConfig.GEMINI_DEBATE_INNOVATOR) ChatLanguageModel innovatorChatModel,
            @Qualifier(AiConfig.GEMINI_DEBATE_SKEPTIC) ChatLanguageModel skepticChatModel,
            ObjectMapper objectMapper,
            StrategyInsightService strategyInsightService,
            CreditVaultService creditVaultService) {
        this.directorChatModel = Objects.requireNonNull(directorChatModel, "directorChatModel");
        this.analystChatModel = Objects.requireNonNull(analystChatModel, "analystChatModel");
        this.innovatorChatModel = Objects.requireNonNull(innovatorChatModel, "innovatorChatModel");
        this.skepticChatModel = Objects.requireNonNull(skepticChatModel, "skepticChatModel");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.strategyInsightService =
                Objects.requireNonNull(strategyInsightService, "strategyInsightService");
        this.creditVaultService = Objects.requireNonNull(creditVaultService, "creditVaultService");
    }

    /**
     * ジョブ全体アドバイスを AI で生成する。失敗時は {@link DebateAdviceGenerationException} を投げる。
     * 呼び出し元はテンプレフォールバックを実施すること。
     */
    public StrategyInsight generateForJob(
            List<AuditHistoryEntity> rows, ProjectAdviceContext project, SubscriptionPlan plan) {
        Objects.requireNonNull(rows, "rows");
        Objects.requireNonNull(project, "project");
        SubscriptionPlan resolvedPlan = plan == null ? SubscriptionPlan.STANDARD : plan;

        if (rows.isEmpty()) {
            // 行が無い場合はテンプレ実装と同じ空応答（呼び出し元の整合性のため）
            return new StrategyInsight(null, List.of(), null);
        }

        Double medZ = strategyInsightService.medianModifiedZ(rows);
        Integer medStBox = strategyInsightService.medianVisibilityStage(rows);
        int medSt = medStBox != null ? medStBox : 10;

        // テンプレ4分類の該当文言を「方向性ヒント」として AI に渡す
        StrategyInsight hint =
                medZ != null
                        ? strategyInsightService.fromModifiedZ(medZ)
                        : strategyInsightService.fromVisibilityStage(medSt);

        // PRO/EXPERT かつ課金識別子が揃っている場合は、解析ごとの短縮版議論を起動する。
        if (resolvedPlan.usesProTierFeatures() && project.hasBillingIdentity()) {
            return generateWithShortDebate(rows, project, resolvedPlan, medZ, medSt, hint);
        }
        return generateSingleShot(rows, project, resolvedPlan, medZ, medSt, hint, null);
    }

    /**
     * Pro/Expert: チケットを予約し、短縮版議論を起動して結論を DIRECTOR プロンプトに注入する。
     * 議論〜生成のいずれかが失敗した場合はチケットを全額返金し、Free パス（議論なし単発）へフォールバックする。
     */
    private StrategyInsight generateWithShortDebate(
            List<AuditHistoryEntity> rows,
            ProjectAdviceContext project,
            SubscriptionPlan plan,
            Double medZ,
            int medSt,
            StrategyInsight hint) {
        // 非同期 gap analysis スレッドは ScopedValue（テナントコンテキスト）が未バインドのため、
        // CreditVaultService が要求する organizationId を project から復元して確立する。
        TenantIdentity identity =
                new TenantIdentity(project.organizationId(), project.workspaceId(), null);
        return ScopedValue.where(TenantContextHolder.CONTEXT, identity)
                .call(() -> reserveDebateAndGenerate(rows, project, plan, medZ, medSt, hint));
    }

    private StrategyInsight reserveDebateAndGenerate(
            List<AuditHistoryEntity> rows,
            ProjectAdviceContext project,
            SubscriptionPlan plan,
            Double medZ,
            int medSt,
            StrategyInsight hint) {
        UUID reservationId = creditVaultService.reserve(project.projectId(), DEBATE_CREDIT);
        try {
            String transcript = runShortDebate(rows, project, medZ, medSt);
            StrategyInsight result =
                    generateSingleShot(rows, project, plan, medZ, medSt, hint, transcript);
            creditVaultService.settle(reservationId, DEBATE_CREDIT, "debate_advice_pro");
            SECURITY_AUDIT.info(
                    "advice_generated source=AI_DEBATE plan={} turns={} medZ={}",
                    plan,
                    SHORT_DEBATE_TURNS,
                    medZ != null ? String.format(Locale.ROOT, "%.2f", medZ) : "null");
            return result;
        } catch (RuntimeException exception) {
            // 起動したが失敗 → 全額返金（オーナー決定 2026-05-30）。Free パスへフォールバック（仕様書 F-2）。
            creditVaultService.refund(reservationId);
            SECURITY_AUDIT.info(
                    "advice_debate_refunded plan={} cause={}",
                    plan,
                    exception.getClass().getSimpleName());
            log.warn("pro debate failed, refunded and falling back to single-shot plan={}", plan, exception);
            return generateSingleShot(rows, project, plan, medZ, medSt, hint, null);
        }
    }

    /**
     * DIRECTOR LLM 1 回でアドバイス JSON を生成する（Free パス / 議論結論注入の共通経路）。
     *
     * @param debateTranscript 短縮版議論のトランスクリプト。Free パスでは {@code null}。
     */
    private StrategyInsight generateSingleShot(
            List<AuditHistoryEntity> rows,
            ProjectAdviceContext project,
            SubscriptionPlan plan,
            Double medZ,
            int medSt,
            StrategyInsight hint,
            String debateTranscript) {
        String systemPrompt = buildSystemPrompt(plan, debateTranscript != null);
        String userPrompt = buildUserPrompt(rows, project, medZ, medSt, hint, debateTranscript);

        String rawJson;
        try {
            rawJson = singleChat(systemPrompt, userPrompt, directorChatModel);
        } catch (Exception exception) {
            log.warn(
                    "debate advice LLM call failed plan={} medZ={} cause={}",
                    plan,
                    medZ,
                    exception.toString());
            throw new DebateAdviceGenerationException("LLM call failed", exception);
        }

        DebateAdviceJson parsed;
        try {
            parsed = objectMapper.readValue(stripCodeFence(rawJson), DebateAdviceJson.class);
        } catch (JsonProcessingException jsonProcessingException) {
            log.warn(
                    "debate advice JSON parse failed plan={} raw={}",
                    plan,
                    truncate(rawJson, 500),
                    jsonProcessingException);
            throw new DebateAdviceGenerationException("JSON parse failed", jsonProcessingException);
        }

        String diagnostic = sanitizeDiagnostic(parsed.diagnosticMessage());
        List<String> actions = sanitizeActions(parsed.recommendedActions());

        if (diagnostic.isEmpty() || actions.isEmpty()) {
            throw new DebateAdviceGenerationException("LLM returned empty diagnostic or actions");
        }

        if (debateTranscript == null) {
            SECURITY_AUDIT.info(
                    "advice_generated source=AI plan={} medZ={} stage={}",
                    plan,
                    medZ != null ? String.format(Locale.ROOT, "%.2f", medZ) : "null",
                    medSt);
        }

        return new StrategyInsight(diagnostic, actions, medZ);
    }

    /**
     * 短縮版 4 ペルソナ議論を {@link #SHORT_DEBATE_TURNS} ターン回し、トランスクリプトを構築する（SSE なし）。
     * 既存のペルソナ別 ChatLanguageModel ビーンと {@link DebatePersonaSystemPrompts} を流用する。
     */
    private String runShortDebate(
            List<AuditHistoryEntity> rows, ProjectAdviceContext project, Double medZ, int medSt) {
        IndustryType industry = project.industryType();
        String baseContext = buildDebateContext(rows, project, medZ, medSt);
        StringBuilder accumulator = new StringBuilder();

        for (int turn = 0; turn < SHORT_DEBATE_TURNS; turn++) {
            String contextForTurn =
                    accumulator.length() == 0
                            ? baseContext
                            : baseContext + "\n\n## これまでの議論の蓄積\n" + accumulator;

            String analyst =
                    singleChat(
                            DebatePersonaSystemPrompts.forPersona(DebatePersona.ANALYST, industry),
                            contextForTurn,
                            analystChatModel);
            String innovator =
                    singleChat(
                            DebatePersonaSystemPrompts.forPersona(DebatePersona.INNOVATOR, industry),
                            contextForTurn,
                            innovatorChatModel);
            String skepticInput =
                    "アナリストの主張:\n" + analyst + "\n\nイノベーターの主張:\n" + innovator
                            + "\n\n上記の前提・論拠を批判的に検証し、反証可能性と見落としを指摘せよ。";
            String skeptic =
                    singleChat(
                            DebatePersonaSystemPrompts.forPersona(DebatePersona.SKEPTIC, industry),
                            skepticInput,
                            skepticChatModel);

            accumulator
                    .append("\n## ラウンド ")
                    .append(turn + 1)
                    .append("\n### アナリスト\n")
                    .append(analyst)
                    .append("\n### イノベーター\n")
                    .append(innovator)
                    .append("\n### スケプティック\n")
                    .append(skeptic);
        }
        return accumulator.toString();
    }

    private String buildDebateContext(
            List<AuditHistoryEntity> rows, ProjectAdviceContext project, Double medZ, int medSt) {
        StringBuilder sb = new StringBuilder();
        sb.append("以下の GEO 可視性解析結果について議論せよ。\n\n");
        sb.append("【企業プロファイル】\n");
        sb.append("業種: ").append(project.industryType()).append("\n");
        if (project.targetAudience() != null && !project.targetAudience().isBlank()) {
            sb.append("ターゲット顧客像: ").append(project.targetAudience()).append("\n");
        }
        if (project.extractedStrengths() != null && !project.extractedStrengths().isBlank()) {
            sb.append("自社の強み:\n").append(project.extractedStrengths()).append("\n");
        }
        sb.append("\n【解析統計】\n");
        sb.append("解析対象クエリ数: ").append(rows.size()).append("\n");
        if (medZ != null) {
            sb.append("中央値 改Z': ").append(String.format(Locale.ROOT, "%.2f", medZ)).append("\n");
        }
        sb.append("中央値 Visibility Stage: ").append(medSt).append("\n");
        return sb.toString();
    }

    private String buildSystemPrompt(SubscriptionPlan plan, boolean withDebate) {
        String intro =
                withDebate
                        ? "あなたはGEO（Generative Engine Optimization）可視性に特化したシニアアナリストです。"
                                + "提供される4ペルソナ議論の結論・企業プロファイル・解析統計・方向性ヒントを統合し、"
                                + "議論で得られた固有の洞察を反映した戦略アドバイスを JSON で返してください。\n\n"
                        : "あなたはGEO（Generative Engine Optimization）可視性に特化したシニアアナリストです。"
                                + "提供される企業プロファイル・解析統計・方向性ヒントを統合し、"
                                + "業種・ターゲット・自社の強みに固有の文脈を持った戦略アドバイスを JSON で返してください。\n\n";
        return intro
                + "出力 JSON スキーマ:\n"
                + "{\n"
                + "  \"diagnostic_message\": \"" + DIAGNOSTIC_MAX_CHARS + "文字以内の日本語の戦略診断\",\n"
                + "  \"recommended_actions\": [\"" + ACTION_MAX_CHARS + "文字以内の改善案1\", \"改善案2\", \"改善案3\"]\n"
                + "}\n\n"
                + "重要:\n"
                + "- diagnostic_message は方向性ヒントの丸写しを禁止。業種・ターゲット・強みの固有要素を必ず含めること。\n"
                + "- recommended_actions は必ず3件、各" + ACTION_MAX_CHARS + "文字以内、日本語、命令形で簡潔に。\n"
                + "- JSON 以外の文字（前置き・後置き・コードフェンス）は出力しないこと。";
    }

    private String buildUserPrompt(
            List<AuditHistoryEntity> rows,
            ProjectAdviceContext project,
            Double medZ,
            int medSt,
            StrategyInsight hint,
            String debateTranscript) {
        StringBuilder sb = new StringBuilder();
        sb.append("【企業プロファイル】\n");
        sb.append("業種: ").append(project.industryType()).append("\n");
        if (project.targetAudience() != null && !project.targetAudience().isBlank()) {
            sb.append("ターゲット顧客像: ").append(project.targetAudience()).append("\n");
        }
        if (project.extractedStrengths() != null
                && !project.extractedStrengths().isBlank()) {
            sb.append("自社の強み:\n").append(project.extractedStrengths()).append("\n");
        }

        sb.append("\n【解析統計】\n");
        sb.append("解析対象クエリ数: ").append(rows.size()).append("\n");
        if (medZ != null) {
            sb.append("中央値 改Z': ").append(String.format(Locale.ROOT, "%.2f", medZ)).append("\n");
        }
        sb.append("中央値 Visibility Stage: ").append(medSt).append("\n");

        if (debateTranscript != null && !debateTranscript.isBlank()) {
            sb.append("\n【4ペルソナ議論の結論（最重要、これを反映せよ）】\n");
            sb.append(debateTranscript).append("\n");
        }

        sb.append("\n【方向性ヒント（参考、丸写し禁止）】\n");
        if (hint.diagnosticMessage() != null) {
            sb.append(hint.diagnosticMessage()).append("\n");
        }
        if (!hint.recommendedActions().isEmpty()) {
            sb.append("参考アクション例:\n");
            for (String act : hint.recommendedActions()) {
                sb.append("- ").append(act).append("\n");
            }
        }

        sb.append("\n上記を踏まえ、業種・ターゲット・強みに即した固有のアドバイスを JSON で出力してください。");
        return sb.toString();
    }

    private String singleChat(String systemPrompt, String userContent, ChatLanguageModel model) {
        return model.chat(
                        ChatRequest.builder()
                                .messages(SystemMessage.from(systemPrompt), UserMessage.from(userContent))
                                .build())
                .aiMessage()
                .text();
    }

    private static String stripCodeFence(String s) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        if (t.startsWith("```")) {
            int firstNl = t.indexOf('\n');
            if (firstNl > 0) {
                t = t.substring(firstNl + 1);
            }
            if (t.endsWith("```")) {
                t = t.substring(0, t.length() - 3);
            }
        }
        return t.trim();
    }

    private static String sanitizeDiagnostic(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.length() <= DIAGNOSTIC_MAX_CHARS) {
            return trimmed;
        }
        return trimmed.substring(0, DIAGNOSTIC_MAX_CHARS);
    }

    private static List<String> sanitizeActions(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>(RECOMMENDED_ACTIONS_COUNT);
        for (String a : raw) {
            if (a == null) {
                continue;
            }
            String t = a.trim();
            if (t.isEmpty()) {
                continue;
            }
            if (t.length() > ACTION_MAX_CHARS) {
                t = t.substring(0, ACTION_MAX_CHARS);
            }
            result.add(t);
            if (result.size() >= RECOMMENDED_ACTIONS_COUNT) {
                break;
            }
        }
        return List.copyOf(result);
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DebateAdviceJson(
            @JsonProperty("diagnostic_message") String diagnosticMessage,
            @JsonProperty("recommended_actions") List<String> recommendedActions) {}

    /** LLM 呼び出し or パース失敗を表す内部例外。呼び出し元はテンプレフォールバックを実施すること。 */
    public static final class DebateAdviceGenerationException extends RuntimeException {
        public DebateAdviceGenerationException(String message) {
            super(message);
        }

        public DebateAdviceGenerationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
