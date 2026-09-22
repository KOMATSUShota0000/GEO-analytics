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
import com.geo.analytics.domain.enums.RoadmapPhase;
import com.geo.analytics.domain.enums.SubscriptionPlan;
import com.geo.analytics.domain.model.MinorityReport;
import com.geo.analytics.domain.model.RemediationTask;
import com.geo.analytics.domain.model.RoadmapItem;
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
import java.util.Comparator;
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

    /** Why: マイノリティ・レポートは JSONB に載せて画面と PDF に出す。無制限に長いと表示が壊れるため上限を置く（#80）。 */
    private static final int MINORITY_REPORT_MAX_COUNT = 2;
    private static final int MINORITY_FIELD_MAX_CHARS = 200;

    /**
     * Why: ロードマップは3段×最大2件。多すぎると「順番の提案」ではなくタスク一覧の再掲になる（#77）。
     * 改善タスクがある回は、各フェーズ1件の時間割にする（#141）。
     */
    private static final int ROADMAP_MAX_COUNT = 6;
    private static final int ROADMAP_TITLE_MAX_CHARS = 60;
    private static final int ROADMAP_TEXT_MAX_CHARS = 120;

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
            @Qualifier(AiConfig.GEMINI_DEBATE_ADVICE_DIRECTOR) ChatLanguageModel directorChatModel,
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
     * ジョブ全体アドバイスと、合意に入らなかった尖った提案（マイノリティ・レポート）。
     *
     * <p>Why: {@code StrategyInsight} は16箇所で生成されており、そこへ項目を足すと無関係な経路まで
     * 巻き込む。議論の成果物として別の器で返す（#80）。
     */
    public record JobAdvice(
            StrategyInsight insight, List<MinorityReport> minorityReports, List<RoadmapItem> roadmapItems) {
        public JobAdvice {
            minorityReports = minorityReports == null ? List.of() : List.copyOf(minorityReports);
            roadmapItems = roadmapItems == null ? List.of() : List.copyOf(roadmapItems);
        }
    }

    /**
     * ジョブ全体アドバイスを AI で生成する。失敗時は {@link DebateAdviceGenerationException} を投げる。
     * 呼び出し元はテンプレフォールバックを実施すること。
     *
     * @param tasks 改善タスク（{@link com.geo.analytics.domain.model.RemediationTaskOrder} の順）。
     *              ロードマップはこの番号で範囲を指す。無い解析では空
     */
    public JobAdvice generateForJob(
            List<AuditHistoryEntity> rows,
            ProjectAdviceContext project,
            SubscriptionPlan plan,
            List<RemediationTask> tasks) {
        Objects.requireNonNull(rows, "rows");
        Objects.requireNonNull(project, "project");
        SubscriptionPlan resolvedPlan = plan == null ? SubscriptionPlan.STANDARD : plan;
        List<RemediationTask> orderedTasks = tasks == null ? List.of() : List.copyOf(tasks);

        if (rows.isEmpty()) {
            // 行が無い場合はテンプレ実装と同じ空応答（呼び出し元の整合性のため）
            return new JobAdvice(new StrategyInsight(null, List.of(), null), List.of(), List.of());
        }

        Double medZ = strategyInsightService.medianModifiedZ(rows);
        Integer medStBox = strategyInsightService.medianVisibilityStage(rows);
        int medSt = medStBox != null ? medStBox : 10;

        // テンプレ4分類の該当文言を「方向性ヒント」として AI に渡す
        StrategyInsight hint =
                medZ != null
                        ? strategyInsightService.fromModifiedZ(medZ)
                        : strategyInsightService.fromVisibilityStage(medSt);

        // Why: 4ペルソナ議論は全プランで走らせる（オーナー確定 2026-09-20 / #73）。CLAUDE.md がプロダクトの
        //      核の第一項に挙げる体験であり、プランで有無を分けると「改善提案の質」がプランで別物になる。
        //      課金識別子が揃わない場合だけは、チケットを予約できないため単発生成へ落とす。
        if (project.hasBillingIdentity()) {
            return generateWithShortDebate(rows, project, resolvedPlan, medZ, medSt, hint, orderedTasks);
        }
        return generateSingleShot(rows, project, resolvedPlan, medZ, medSt, hint, orderedTasks, null);
    }

    /**
     * Pro/Expert: チケットを予約し、短縮版議論を起動して結論を DIRECTOR プロンプトに注入する。
     * 議論〜生成のいずれかが失敗した場合はチケットを全額返金し、Free パス（議論なし単発）へフォールバックする。
     */
    private JobAdvice generateWithShortDebate(
            List<AuditHistoryEntity> rows,
            ProjectAdviceContext project,
            SubscriptionPlan plan,
            Double medZ,
            int medSt,
            StrategyInsight hint,
            List<RemediationTask> tasks) {
        // 非同期 gap analysis スレッドは ScopedValue（テナントコンテキスト）が未バインドのため、
        // CreditVaultService が要求する organizationId を project から復元して確立する。
        TenantIdentity identity =
                new TenantIdentity(project.organizationId(), project.workspaceId(), null);
        return ScopedValue.where(TenantContextHolder.CONTEXT, identity)
                .call(() -> reserveDebateAndGenerate(rows, project, plan, medZ, medSt, hint, tasks));
    }

    private JobAdvice reserveDebateAndGenerate(
            List<AuditHistoryEntity> rows,
            ProjectAdviceContext project,
            SubscriptionPlan plan,
            Double medZ,
            int medSt,
            StrategyInsight hint,
            List<RemediationTask> tasks) {
        UUID reservationId = creditVaultService.reserve(project.projectId(), DEBATE_CREDIT);
        try {
            String transcript = runShortDebate(rows, project, medZ, medSt, tasks);
            JobAdvice result = generateSingleShot(rows, project, plan, medZ, medSt, hint, tasks, transcript);
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
            return generateSingleShot(rows, project, plan, medZ, medSt, hint, tasks, null);
        }
    }

    /**
     * DIRECTOR LLM 1 回でアドバイス JSON を生成する（Free パス / 議論結論注入の共通経路）。
     *
     * @param debateTranscript 短縮版議論のトランスクリプト。Free パスでは {@code null}。
     */
    private JobAdvice generateSingleShot(
            List<AuditHistoryEntity> rows,
            ProjectAdviceContext project,
            SubscriptionPlan plan,
            Double medZ,
            int medSt,
            StrategyInsight hint,
            List<RemediationTask> tasks,
            String debateTranscript) {
        String systemPrompt = buildSystemPrompt(debateTranscript != null, !tasks.isEmpty());
        String userPrompt = buildUserPrompt(rows, project, medZ, medSt, hint, tasks, debateTranscript);

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
        if (diagnostic.isEmpty()) {
            throw new DebateAdviceGenerationException("LLM returned empty diagnostic");
        }

        if (debateTranscript == null) {
            SECURITY_AUDIT.info(
                    "advice_generated source=AI plan={} medZ={} stage={}",
                    plan,
                    medZ != null ? String.format(Locale.ROOT, "%.2f", medZ) : "null",
                    medSt);
        }

        // Why: 推奨アクションは作らせない。やることは改善タスク、いつやるかはロードマップが受け持ち、
        //      3つ目のやることリストがあると何をやればよいかわからなくなる（オーナー確定 2026-09-22 / #141）。
        return new JobAdvice(
                new StrategyInsight(diagnostic, List.of(), medZ),
                toMinorityReports(parsed),
                toRoadmapItems(parsed, tasks.size()));
    }

    /**
     * Why: フェーズ順に並べ替えてから保存する。LLM が返す順序は保証されず、画面側で毎回並べ替えるより
     * 保存時に確定させたほうが PDF・API・画面で同じ並びになる（#77）。
     *
     * @param taskCount 改善タスクの件数。1件以上なら各フェーズにタスク番号の範囲を割り当てる（#141）
     */
    static List<RoadmapItem> toRoadmapItems(DebateAdviceJson parsed, int taskCount) {
        if (parsed.roadmapItems() == null || parsed.roadmapItems().isEmpty()) {
            return List.of();
        }
        List<RoadmapItemJson> drafts = new ArrayList<>(ROADMAP_MAX_COUNT);
        for (RoadmapItemJson raw : parsed.roadmapItems()) {
            if (raw == null
                    || parsePhase(raw.phase()) == null
                    || clamp(raw.title(), ROADMAP_TITLE_MAX_CHARS).isEmpty()) {
                continue;
            }
            drafts.add(raw);
            if (drafts.size() >= ROADMAP_MAX_COUNT) {
                break;
            }
        }
        drafts.sort(Comparator.comparingInt(d -> parsePhase(d.phase()).ordinal()));
        if (taskCount <= 0) {
            List<RoadmapItem> out = new ArrayList<>(drafts.size());
            for (RoadmapItemJson d : drafts) {
                out.add(toRoadmapItem(d, null, null));
            }
            return List.copyOf(out);
        }
        return assignTaskRanges(drafts, taskCount);
    }

    /**
     * Why: 改善タスクは番号順に進めるため、各フェーズは前のフェーズの続きから始まる連続した範囲でなければならない。
     * AI が返す番号には重なり・抜け・逆順がありうるので、フェーズ順に詰めて補正し、最後のフェーズを最後の
     * タスクで閉じる。同じフェーズの2件目は時間割が二重になるため落とす（#141）。
     */
    private static List<RoadmapItem> assignTaskRanges(List<RoadmapItemJson> sortedDrafts, int taskCount) {
        List<RoadmapItem> out = new ArrayList<>(RoadmapPhase.values().length);
        RoadmapPhase previousPhase = null;
        int done = 0;
        for (RoadmapItemJson d : sortedDrafts) {
            RoadmapPhase phase = parsePhase(d.phase());
            if (phase == previousPhase || done >= taskCount) {
                continue;
            }
            int requested = d.lastTaskNumber() == null ? done + 1 : d.lastTaskNumber();
            int last = StrictMath.max(done + 1, StrictMath.min(taskCount, requested));
            out.add(toRoadmapItem(d, done + 1, last));
            done = last;
            previousPhase = phase;
        }
        if (!out.isEmpty() && done < taskCount) {
            RoadmapItem tail = out.removeLast();
            out.add(new RoadmapItem(
                    tail.phase(), tail.title(), tail.rationale(), tail.expectedImpact(),
                    tail.firstTaskNumber(), taskCount));
        }
        return List.copyOf(out);
    }

    private static RoadmapItem toRoadmapItem(RoadmapItemJson d, Integer firstTask, Integer lastTask) {
        return new RoadmapItem(
                parsePhase(d.phase()),
                clamp(d.title(), ROADMAP_TITLE_MAX_CHARS),
                clamp(d.rationale(), ROADMAP_TEXT_MAX_CHARS),
                clamp(d.expectedImpact(), ROADMAP_TEXT_MAX_CHARS),
                firstTask,
                lastTask);
    }

    private static RoadmapPhase parsePhase(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return RoadmapPhase.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /** Why: 空の項目しか無いレポートは提案の材料にならないため落とす（#80）。 */
    private static List<MinorityReport> toMinorityReports(DebateAdviceJson parsed) {
        if (parsed.minorityReports() == null || parsed.minorityReports().isEmpty()) {
            return List.of();
        }
        List<MinorityReport> out = new ArrayList<>(MINORITY_REPORT_MAX_COUNT);
        for (MinorityReportJson raw : parsed.minorityReports()) {
            if (raw == null) {
                continue;
            }
            String insight = clamp(raw.insight(), MINORITY_FIELD_MAX_CHARS);
            if (insight.isEmpty()) {
                continue;
            }
            out.add(
                    new MinorityReport(
                            insight,
                            clamp(raw.conflictReason(), MINORITY_FIELD_MAX_CHARS),
                            clamp(raw.evidence(), MINORITY_FIELD_MAX_CHARS)));
            if (out.size() >= MINORITY_REPORT_MAX_COUNT) {
                break;
            }
        }
        return List.copyOf(out);
    }

    private static String clamp(String raw, int maxChars) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        return trimmed.length() <= maxChars ? trimmed : trimmed.substring(0, maxChars);
    }

    /**
     * 短縮版 4 ペルソナ議論を {@link #SHORT_DEBATE_TURNS} ターン回し、トランスクリプトを構築する（SSE なし）。
     * 既存のペルソナ別 ChatLanguageModel ビーンと {@link DebatePersonaSystemPrompts} を流用する。
     */
    private String runShortDebate(
            List<AuditHistoryEntity> rows,
            ProjectAdviceContext project,
            Double medZ,
            int medSt,
            List<RemediationTask> tasks) {
        IndustryType industry = project.industryType();
        String baseContext = buildDebateContext(rows, project, medZ, medSt, tasks);
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
            List<AuditHistoryEntity> rows,
            ProjectAdviceContext project,
            Double medZ,
            int medSt,
            List<RemediationTask> tasks) {
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
        appendTaskList(sb, tasks);
        return sb.toString();
    }

    /**
     * Why: 議論はサイトの中身（診断で見つかった不足）を知らずに一般論のロードマップを出し、改善タスクと
     * 「今すぐやること」が食い違っていた（#141）。改善タスクを取り組む順の番号付きで渡し、ロードマップは
     * この番号で時間割を組ませる。タイトルとラベルだけにして、4人の議論のトークンを増やしすぎない。
     */
    static void appendTaskList(StringBuilder sb, List<RemediationTask> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return;
        }
        sb.append("\n【改善タスク（全").append(tasks.size()).append("件。番号は取り組む順）】\n");
        for (int i = 0; i < tasks.size(); i++) {
            RemediationTask t = tasks.get(i);
            sb.append(i + 1)
                    .append(". [")
                    .append(t.priority().label())
                    .append("・")
                    .append(t.category().label())
                    .append("] ")
                    .append(t.title())
                    .append("\n");
        }
    }

    /**
     * Why: 出力は代理店がクライアントに見せる画面とレポートにそのまま載る。入力の指標名（改Z'・Visibility Stage）
     * や英語の用語がロードマップへ写っていたため、改善タスクと同じ書き方のルールを課す（#139 / #141）。
     */
    private String buildSystemPrompt(boolean withDebate, boolean withTasks) {
        String intro =
                withDebate
                        ? "あなたはGEO（Generative Engine Optimization）可視性に特化したシニアアナリストです。"
                                + "提供される4ペルソナ議論の結論・企業プロファイル・解析統計・方向性ヒントを統合し、"
                                + "議論で得られた固有の洞察を反映した戦略アドバイスを JSON で返してください。\n\n"
                        : "あなたはGEO（Generative Engine Optimization）可視性に特化したシニアアナリストです。"
                                + "提供される企業プロファイル・解析統計・方向性ヒントを統合し、"
                                + "業種・ターゲット・自社の強みに固有の文脈を持った戦略アドバイスを JSON で返してください。\n\n";
        String roadmapRule =
                withTasks
                        ? "- roadmap_items は【改善タスク】の時間割。改善タスクは番号順に進める。phase ごとに1件ずつ、"
                                + "NOW（今すぐ）→ SHORT_TERM（1〜3ヶ月）→ MID_TERM（3〜6ヶ月）の順に置くこと。"
                                + "last_task_number は、そのフェーズで最後に終える改善タスクの番号。NOW はタスク1から始まり、"
                                + "次のフェーズは前のフェーズの次の番号から始まる。最後のフェーズの last_task_number は"
                                + "最後のタスクの番号にすること。「すぐ直せる」タスクは早いフェーズに、「時間がかかる」タスクは"
                                + "期間の長いフェーズに収まるよう、所要時間を見て区切ること。タスクが少なければフェーズを減らしてよい。"
                                + "title はそのフェーズで目指すこと、rationale はなぜこの時期にこの順番で進めるのか、"
                                + "expected_impact はそのフェーズを終えると何が変わるかを書くこと。"
                                + "改善タスクのタイトルの丸写しは禁止。\n"
                        : "- roadmap_items は改善ロードマップ。phase は NOW（今すぐ）/ SHORT_TERM（1〜3ヶ月）/ "
                                + "MID_TERM（3〜6ヶ月）のいずれか。3〜" + ROADMAP_MAX_COUNT + "件、各フェーズに最低1件を置き、"
                                + "前のフェーズの成果の上に次が積み上がる順序にすること。last_task_number は 0 にすること。\n";
        return intro
                + "出力 JSON スキーマ:\n"
                + "{\n"
                + "  \"diagnostic_message\": \"" + DIAGNOSTIC_MAX_CHARS + "文字以内の日本語の総合診断\",\n"
                + "  \"minority_reports\": [{\"insight\": \"合意に入らなかった尖った提案\","
                + " \"conflict_reason\": \"採択しなかった理由（批判の要点と、どんな文脈なら化けるか）\","
                + " \"evidence\": \"入力のどこに拠り所があるか\"}],\n"
                + "  \"roadmap_items\": [{\"phase\": \"NOW|SHORT_TERM|MID_TERM\","
                + " \"last_task_number\": 整数,"
                + " \"title\": \"" + ROADMAP_TITLE_MAX_CHARS + "文字以内\","
                + " \"rationale\": \"なぜこのフェーズなのか\","
                + " \"expected_impact\": \"見込まれる効果\"}]\n"
                + "}\n\n"
                + "読み手と書き方:\n"
                + "- 読み手は Web 制作会社・代理店の担当者と、その先のクライアント（経営者・広報など）。"
                + "エンジニアとは限らず、出力はそのまま画面とレポートに載る。\n"
                + "- 平易な日本語の「です・ます」調で書くこと。「LLM」とは書かず「AI」と書くこと。\n"
                + "- 入力の指標名（改Z'、Visibility Stage など）や、英語の専門用語（Brand Recommendation、"
                + "Information Gain など）をそのまま書かないこと。数値の意味を言葉で説明すること。\n\n"
                + "重要:\n"
                + "- diagnostic_message は、いま AI の回答で自社がどう扱われているか、その理由は何かを説明する現状診断。"
                + "やることの列挙はしないこと（やることは改善タスクとロードマップが受け持つ）。"
                + "方向性ヒントの丸写しを禁止。業種・ターゲット・強みの固有要素を必ず含めること。\n"
                + "- minority_reports は0〜2件。合意案に入れなかったが捨てるに惜しい案があるときだけ書くこと。"
                + "無理に埋めず、無ければ空配列にすること。evidence には入力に無い内容を書いてはならない。\n"
                + roadmapRule
                + "- JSON 以外の文字（前置き・後置き・コードフェンス）は出力しないこと。\n";
    }

    private String buildUserPrompt(
            List<AuditHistoryEntity> rows,
            ProjectAdviceContext project,
            Double medZ,
            int medSt,
            StrategyInsight hint,
            List<RemediationTask> tasks,
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
        appendTaskList(sb, tasks);

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

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record DebateAdviceJson(
            @JsonProperty("diagnostic_message") String diagnosticMessage,
            @JsonProperty("minority_reports") List<MinorityReportJson> minorityReports,
            @JsonProperty("roadmap_items") List<RoadmapItemJson> roadmapItems) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RoadmapItemJson(
            @JsonProperty("phase") String phase,
            @JsonProperty("last_task_number") Integer lastTaskNumber,
            @JsonProperty("title") String title,
            @JsonProperty("rationale") String rationale,
            @JsonProperty("expected_impact") String expectedImpact) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record MinorityReportJson(
            @JsonProperty("insight") String insight,
            @JsonProperty("conflict_reason") String conflictReason,
            @JsonProperty("evidence") String evidence) {}

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
