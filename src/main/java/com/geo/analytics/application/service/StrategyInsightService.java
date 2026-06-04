package com.geo.analytics.application.service;

import com.geo.analytics.application.dto.ProjectAdviceContext;
import com.geo.analytics.application.dto.StrategyInsight;
import com.geo.analytics.domain.entity.AuditHistoryEntity;
import com.geo.analytics.domain.enums.AdviceSource;
import com.geo.analytics.domain.enums.SubscriptionPlan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import java.lang.StrictMath;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public final class StrategyInsightService {
    private static final Logger log = LoggerFactory.getLogger(StrategyInsightService.class);
    private static final Logger SECURITY_AUDIT = LoggerFactory.getLogger("SECURITY_AUDIT");

    public static final String GAP_ANALYSIS_VERSION = "GAP_ANALYSIS_V1";
    public static final String REL_BASELINE_VERSION = "REL_BASELINE_V1";

    /**
     * DebateAdviceGeneratorService は循環依存を避けるため ObjectProvider 経由で遅延解決する
     * （DebateAdviceGeneratorService が StrategyInsightService.fromModifiedZ をヒント生成に使うため）。
     */
    private final ObjectProvider<DebateAdviceGeneratorService> debateAdviceGeneratorProvider;

    public StrategyInsightService(
            ObjectProvider<DebateAdviceGeneratorService> debateAdviceGeneratorProvider) {
        this.debateAdviceGeneratorProvider =
                Objects.requireNonNull(debateAdviceGeneratorProvider, "debateAdviceGeneratorProvider");
    }
    private static final String MSG_DOMINATOR =
        "【市場の支配者】貴社のGBVSスコアは市場の中央値から+2σ以上という極めて特異な水準に達しています。現在のAIモデル群は、該当トピックに関して貴社を『絶対的な情報源(Absolute Authority)』として認識しており、Generative Engine内でのSoM(モデル占有率)は極めて強固です。現在のオウンドメディア資産と外部からのサイテーション戦略を維持し、次世代モデルの学習データセットにおいても先行優位性を保つための保守戦略へ移行してください。";
    private static final String MSG_STRONG =
        "【有力な選択肢】貴社のGBVSスコアは市場の平均を優に上回っており(+0.5σ〜)、AIの回答生成において高い確率で上位言及を獲得しています。VLP(可視性対数減衰)スコアの評価をさらに最大化するためには、文脈の専門性をさらに高め、AIが長文の回答における『第一段落』で貴社を引用するような、より権威的な一次情報の構築にフォーカスしてください。";
    private static final String MSG_REDOCEAN =
        "【競争の境界線】貴社の可視性は市場の中央値付近(±1σ以内)に密集しており、統計的に最も層の厚いレッドオーシャンに位置しています。AIは貴社の存在を認知してはいるものの、競合他社との有意な差別化要因を抽出できていません。Modified BM25における『密度(Length)』評価を高めるため、自社独自のデータセットやユースケースの公開を強化してください。";
    private static final String MSG_BLINDSPOT =
        "【デジタル上の死角】貴社のスコアは市場基準を大きく下回っており(-1σ以下)、現在の主要なAIモデルはユーザーへの回答生成において貴社をほとんど参照していません(AI Blindspot)。まずはLLMクローラーが学習しやすい構造化データ(Schema.org等)の基盤整備と、権威ある外部ソース(PR記事や専門誌)からのサイテーション獲得(Earned Media)を最優先の急務として設定してください。";
    private static final List<String> ACTIONS_LOW = List.of(
        "Schema.orgのJSON-LD実装",
        "権威メディアへのPR投稿",
        "統計データの公開");
    private static final List<String> ACTIONS_HIGH = List.of(
        "ブランド指名検索の強化",
        "引用元データの更新",
        "独自用語の定義維持");

    public StrategyInsight fromModifiedZ(double zPrime) {
        var z = zPrime;
        if (z >= 2.0) {
            return new StrategyInsight(MSG_DOMINATOR, ACTIONS_HIGH, z);
        }
        if (z >= 0.5) {
            return new StrategyInsight(MSG_STRONG, ACTIONS_HIGH, z);
        }
        if (z >= -1.0) {
            return new StrategyInsight(MSG_REDOCEAN, ACTIONS_LOW, z);
        }
        return new StrategyInsight(MSG_BLINDSPOT, ACTIONS_LOW, z);
    }

    public StrategyInsight fromVisibilityStage(int stage) {
        var s = StrictMath.max(1, StrictMath.min(10, stage));
        if (s <= 1) {
            return new StrategyInsight(MSG_DOMINATOR, ACTIONS_HIGH, 2.0);
        }
        if (s <= 4) {
            return new StrategyInsight(MSG_STRONG, ACTIONS_HIGH, 1.0);
        }
        if (s <= 7) {
            return new StrategyInsight(MSG_REDOCEAN, ACTIONS_LOW, 0.0);
        }
        return new StrategyInsight(MSG_BLINDSPOT, ACTIONS_LOW, -1.5);
    }

    /** SoM 絶対値の帯ごとの基本方針（Tier 統一しきい値 6/16/31）。 */
    private record TierAdvice(String tier, String nextMove, List<String> actions) {}

    private static TierAdvice tierAdviceFromSom(double som) {
        if (som >= 31.0) {
            return new TierAdvice(
                "Market Leader（市場リーダー）",
                "AIが『この分野の結論』として貴社を参照し続けるよう、PRTIMES発信や専門メディアでの独占情報で防衛的に維持してください。",
                ACTIONS_HIGH);
        }
        if (som >= 16.0) {
            return new TierAdvice(
                "Competitive（競争力あり）",
                "主要な選択肢として認知されています。独自調査レポートや一次情報を増やし、AIが引用する権威ノードを拡張してください。",
                ACTIONS_HIGH);
        }
        if (som >= 6.0) {
            return new TierAdvice(
                "Challenger（チャレンジャー）",
                "言及は獲得できていますが、推奨の文脈で選ばれる『信頼の差』が残ります。第三者レビュー・比較サイト・PR露出を増やしてください。",
                ACTIONS_LOW);
        }
        return new TierAdvice(
            "Invisible（存在なし）",
            "AI上でブランドがほぼ不可視です。Schema.org 等の構造化データ整備と権威メディアからのサイテーション獲得を最優先にしてください。",
            ACTIONS_LOW);
    }

    /**
     * 定型文を排し、その解析の実測値（SoM 絶対値・引用順位）を文に埋め込んだ動的な診断を返す。
     * SoM が動けば点数・ティア・次の一手がすべて変わるため、解析ごとに異なる文になる（ADR-018）。
     */
    public StrategyInsight describeForQuery(Double som, Integer aiPos) {
        return describeForQuery(som, aiPos, null);
    }

    /**
     * {@link #describeForQuery(Double, Integer)} に加え、ルーブリック監査が抽出した
     * サイト固有の優先改善タスク（{@code prioritizedTasks} の title）で後半アドバイスと
     * 推奨アクションを動的化する（ADR-020）。
     * タスクが空・null の場合は SoM 帯テンプレートへ安全にフォールバックする。
     */
    public StrategyInsight describeForQuery(Double som, Integer aiPos, List<String> siteTasks) {
        double s = som != null ? som : 0.0;
        TierAdvice ta = tierAdviceFromSom(s);
        String citation = (aiPos != null && aiPos > 0)
            ? String.format(Locale.ROOT, "AI回答内で%d番目の推奨として引用されています", aiPos)
            : "順位付き推奨リストには未掲載です";
        List<String> tasks = sanitizeSiteTasks(siteTasks);
        String nextMove;
        List<String> actions;
        if (tasks.isEmpty()) {
            nextMove = ta.nextMove();
            actions = ta.actions();
        } else {
            // サイト固有の最優先改善を後半文に昇格させ、帯固定文を排する。
            nextMove = String.format(Locale.ROOT, "このサイト固有の最優先改善は「%s」です。", tasks.get(0));
            actions = tasks;
        }
        String diag = String.format(
            Locale.ROOT,
            "現在のSoM可視性は%.1f点（%sティア）。%s。%s",
            s, ta.tier(), citation, nextMove);
        return new StrategyInsight(diag, actions, null);
    }

    /**
     * サイト固有タスク（title）を整形する: 空白除去・重複排除・帯固定テンプレ文の除外・上限3件。
     * 帯固定テンプレ（{@link #ACTIONS_LOW}/{@link #ACTIONS_HIGH}）はサイト固有ではないため除外し、
     * 定型文がアドバイスに混入するのを防ぐ。
     */
    private static List<String> sanitizeSiteTasks(List<String> siteTasks) {
        if (siteTasks == null || siteTasks.isEmpty()) {
            return List.of();
        }
        ArrayList<String> out = new ArrayList<>(3);
        for (String task : siteTasks) {
            if (task == null) {
                continue;
            }
            String trimmed = task.strip();
            if (trimmed.isEmpty()
                    || ACTIONS_LOW.contains(trimmed)
                    || ACTIONS_HIGH.contains(trimmed)
                    || out.contains(trimmed)) {
                continue;
            }
            out.add(trimmed);
            if (out.size() >= 3) {
                break;
            }
        }
        return List.copyOf(out);
    }

    /**
     * ジョブ全体の動的サマリー。クエリ数と SoM 分布（中央値・最高・最低）を文に埋め込む。
     * 各クエリに蓄積されたサイト固有タスク（{@code siteTasks}）があれば後半アドバイスと
     * 推奨アクションを動的化し、無ければ SoM 帯テンプレートへフォールバックする（ADR-020）。
     */
    private StrategyInsight describeJobRollup(
            double medSom,
            double minSom,
            double maxSom,
            int queryCount,
            Double representativeZ,
            List<String> siteTasks) {
        TierAdvice ta = tierAdviceFromSom(medSom);
        String spread = queryCount > 1
            ? String.format(
                Locale.ROOT,
                "%d件のクエリを解析。SoM中央値は%.1f点（最高%.1f／最低%.1f）",
                queryCount, medSom, maxSom, minSom)
            : String.format(Locale.ROOT, "解析したクエリのSoM可視性は%.1f点", medSom);
        List<String> tasks = sanitizeSiteTasks(siteTasks);
        String nextMove = tasks.isEmpty()
            ? ta.nextMove()
            : String.format(Locale.ROOT, "このサイト固有の最優先改善は「%s」です。", tasks.get(0));
        List<String> actions = tasks.isEmpty() ? ta.actions() : tasks;
        String diag = String.format(Locale.ROOT, "%s（%sティア）。%s", spread, ta.tier(), nextMove);
        return new StrategyInsight(diag, actions, representativeZ);
    }

    /** 各監査行に蓄積されたサイト固有の推奨アクションを集約する（帯固定テンプレは sanitize 側で除外）。 */
    private static List<String> collectSiteTasksFromRows(List<AuditHistoryEntity> rows) {
        ArrayList<String> aggregated = new ArrayList<>();
        for (AuditHistoryEntity row : rows) {
            List<String> actions = row.getRecommendedActions();
            if (actions != null) {
                aggregated.addAll(actions);
            }
        }
        return aggregated;
    }

    public StrategyInsight resolveForAudit(AuditHistoryEntity auditHistoryEntity) {
        var diag = auditHistoryEntity.getDiagnosticMessage();
        var acts = auditHistoryEntity.getRecommendedActions();
        var mz = auditHistoryEntity.getModifiedZScore();
        if (diag != null && !diag.isBlank() && acts != null && !acts.isEmpty()) {
            return new StrategyInsight(diag, List.copyOf(acts), mz);
        }
        var som = auditHistoryEntity.getSomScore();
        if (som != null) {
            return describeForQuery(som, auditHistoryEntity.getAiCitationPosition());
        }
        var vs = auditHistoryEntity.getVisibilityStage();
        return fromVisibilityStage(vs != null ? vs : 10);
    }

    /**
     * テンプレート4分類のみで roll up する旧実装。
     * AI 議論駆動の {@link #rollupJob(List, ProjectEntity, SubscriptionPlan)} が失敗した時の
     * フォールバックとして使用される。
     */
    public StrategyInsight rollupJobFromTemplate(List<AuditHistoryEntity> rows) {
        if (rows == null || rows.isEmpty()) {
            return new StrategyInsight(null, List.of(), null);
        }
        var soms = rows.stream()
            .map(AuditHistoryEntity::getSomScore)
            .filter(Objects::nonNull)
            .mapToDouble(Double::doubleValue)
            .sorted()
            .toArray();
        // 改Z'（ジョブ内相対指標）は数値表示用に温存し、診断文は SoM 絶対値で動的生成する。
        Double medZ = medianModifiedZ(rows);
        if (soms.length == 0) {
            var stages = rows.stream()
                .map(AuditHistoryEntity::getVisibilityStage)
                .map(s -> s != null ? s : 10)
                .sorted()
                .mapToInt(Integer::intValue)
                .toArray();
            return fromVisibilityStage(medianInt(stages));
        }
        var medSom = medianSorted(soms);
        return describeJobRollup(
            medSom, soms[0], soms[soms.length - 1], rows.size(), medZ, collectSiteTasksFromRows(rows));
    }

    /**
     * 既存呼び出し元（project / plan 情報を持たない箇所）向けの後方互換 API。
     * AI 駆動には文脈不足のため、テンプレート版にそのまま委譲する。
     */
    public StrategyInsight rollupJob(List<AuditHistoryEntity> rows) {
        return rollupJobFromTemplate(rows);
    }

    /** ジョブ全体アドバイスと、その生成元（AI / テンプレフォールバック）を束ねた結果。 */
    public record JobAdviceRollup(StrategyInsight insight, AdviceSource source) {}

    /**
     * AI 議論駆動でジョブ全体アドバイスを生成する。
     * {@link DebateAdviceGeneratorService} がコンテキスト不足や LLM 障害で失敗した場合は
     * テンプレート版にフォールバックする。
     */
    public StrategyInsight rollupJob(
            List<AuditHistoryEntity> rows, ProjectAdviceContext project, SubscriptionPlan plan) {
        return rollupJobWithSource(rows, project, plan).insight();
    }

    /**
     * {@link #rollupJob(List, ProjectAdviceContext, SubscriptionPlan)} と同じだが、
     * 生成元（{@link AdviceSource}）も返す。フォールバック時のバッジ表示（F-3.1）に用いる。
     */
    public JobAdviceRollup rollupJobWithSource(
            List<AuditHistoryEntity> rows, ProjectAdviceContext project, SubscriptionPlan plan) {
        if (rows == null || rows.isEmpty() || project == null) {
            return new JobAdviceRollup(rollupJobFromTemplate(rows), AdviceSource.TEMPLATE_FALLBACK);
        }
        DebateAdviceGeneratorService generator = debateAdviceGeneratorProvider.getIfAvailable();
        if (generator == null) {
            log.debug("DebateAdviceGeneratorService unavailable, falling back to template");
            return new JobAdviceRollup(rollupJobFromTemplate(rows), AdviceSource.TEMPLATE_FALLBACK);
        }
        try {
            return new JobAdviceRollup(generator.generateForJob(rows, project, plan), AdviceSource.AI);
        } catch (RuntimeException exception) {
            SECURITY_AUDIT.info(
                    "advice_generated source=TEMPLATE_FALLBACK plan={} cause={}",
                    plan,
                    exception.getClass().getSimpleName());
            log.warn(
                    "AI advice generation failed, falling back to template plan={}",
                    plan,
                    exception);
            return new JobAdviceRollup(rollupJobFromTemplate(rows), AdviceSource.TEMPLATE_FALLBACK);
        }
    }

    public Double medianModifiedZ(List<AuditHistoryEntity> rows) {
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        var arr = rows.stream()
            .map(AuditHistoryEntity::getModifiedZScore)
            .filter(Objects::nonNull)
            .mapToDouble(Double::doubleValue)
            .toArray();
        if (arr.length == 0) {
            return null;
        }
        Arrays.sort(arr);
        return medianSorted(arr);
    }

    public Integer medianVisibilityStage(List<AuditHistoryEntity> rows) {
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        var arr = rows.stream()
            .map(a -> a.getVisibilityStage() != null ? a.getVisibilityStage() : 10)
            .mapToInt(Integer::intValue)
            .toArray();
        Arrays.sort(arr);
        int n = arr.length;
        return arr[(n - 1) / 2];
    }

    public String buildGapAnalystFullPrompt(
            AuditHistoryEntity row,
            double medZ,
            int medSt,
            String jobTrendClip,
            String responseExcerpt) {
        var st = row.getVisibilityStage() != null ? row.getVisibilityStage() : 10;
        var baseline = fromVisibilityStage(st).diagnosticMessage();
        var z = row.getModifiedZScore() != null ? row.getModifiedZScore() : 0.0;
        Integer rk = row.getAiCitationPosition();
        var noun = row.getTokenCount() != null ? row.getTokenCount() : 0;
        return "あなたはGEO可視性アナリストです。Stage別基本戦略テンプレートと実際のAI回答を統合し、diagnostic_messageは200文字以内の日本語、recommended_actionsは3件の日本語改善案のみをJSONで返せ。\n\nStage別基本戦略テンプレート:\n"
            + baseline
            + "\n\nジョブ全体傾向要約:\n"
            + jobTrendClip
            + "\n\nジョブ中央値: 改Z'="
            + String.format(Locale.ROOT, "%.2f", medZ)
            + ", Stage中央値="
            + medSt
            + "\n\n対象クエリ: "
            + row.getQuery()
            + "\n当該改Z': "
            + String.format(Locale.ROOT, "%.2f", z)
            + "\n当該Stage: "
            + st
            + "\n推定GEO可視性ランク: "
            + (rk != null ? rk.toString() : "(言及なし)")
            + "\nブランド出現回数: "
            + noun
            + "\n\nAI回答抜粋:\n"
            + responseExcerpt;
    }

    public StrategyInsight keywordInsightRelative(double zi, double medZ, int stage, int medStage) {
        var msg = "【差分診断】当該キーワードの改Z'はジョブ中央値"
            + String.format(Locale.ROOT, "%.2f", medZ)
            + "に対し"
            + String.format(Locale.ROOT, "%.2f", zi)
            + "であり、Visibility Stageは中央値"
            + medStage
            + "に対し"
            + stage
            + "です。ジョブ全体と比較して統計的に近い帯にあり、極端な乖離は観測されません。";
        var base = fromModifiedZ(medZ);
        return new StrategyInsight(msg, base.recommendedActions(), zi);
    }

    private static double medianSorted(double[] sorted) {
        int n = sorted.length;
        if (n == 0) {
            return 0.0;
        }
        if ((n & 1) == 1) {
            return sorted[n / 2];
        }
        return (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0;
    }

    private static int medianInt(int[] sorted) {
        Arrays.sort(sorted);
        int n = sorted.length;
        if (n == 0) {
            return 10;
        }
        return sorted[(n - 1) / 2];
    }
}
