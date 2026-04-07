package com.geo.analytics.application.service;

import com.geo.analytics.application.dto.StrategyInsight;
import com.geo.analytics.domain.entity.AuditHistoryEntity;
import org.springframework.stereotype.Service;
import java.lang.StrictMath;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public final class StrategyInsightService {
    public static final String GAP_ANALYSIS_VERSION = "GAP_ANALYSIS_V1";
    public static final String REL_BASELINE_VERSION = "REL_BASELINE_V1";
    private static final String MSG_DOMINATOR =
        "【市場の支配者】貴社のGBVSスコアは市場の中央値から+2σ以上という極めて特異な水準に達しています。現在のAIモデル群は、該当トピックに関して貴社を『絶対的な情報源(Absolute Authority)』として認識しており、Generative Engine内でのSoM(モデル占有率)は極めて強固です。現在のオウンドメディア資産と外部からのサイテーション戦略を維持し、次世代モデルの学習データセットにおいても先行優位性を保つための保守戦略へ移行してください。";
    private static final String MSG_STRONG =
        "【有力な選択肢】貴社のGBVSスコアは市場の平均を優に上回っており(+0.5σ〜)、AIの回答生成において高い確率で上位言及を獲得しています。RBP(順位減衰)スコアの評価をさらに最大化するためには、文脈の専門性をさらに高め、AIが長文の回答における『第一段落』で貴社を引用するような、より権威的な一次情報の構築にフォーカスしてください。";
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

    public StrategyInsight resolveForAudit(AuditHistoryEntity auditHistoryEntity) {
        var diag = auditHistoryEntity.getDiagnosticMessage();
        var acts = auditHistoryEntity.getRecommendedActions();
        var mz = auditHistoryEntity.getModifiedZScore();
        if (diag != null && !diag.isBlank() && acts != null && !acts.isEmpty()) {
            return new StrategyInsight(diag, List.copyOf(acts), mz);
        }
        if (mz != null) {
            return fromModifiedZ(mz);
        }
        var vs = auditHistoryEntity.getVisibilityStage();
        return fromVisibilityStage(vs != null ? vs : 10);
    }

    public StrategyInsight rollupJob(List<AuditHistoryEntity> rows) {
        if (rows == null || rows.isEmpty()) {
            return new StrategyInsight(null, List.of(), null);
        }
        var zStream = rows.stream()
            .map(AuditHistoryEntity::getModifiedZScore)
            .filter(Objects::nonNull)
            .mapToDouble(Double::doubleValue);
        var zArr = zStream.toArray();
        if (zArr.length == 0) {
            var stages = rows.stream()
                .map(AuditHistoryEntity::getVisibilityStage)
                .map(s -> s != null ? s : 10)
                .sorted()
                .mapToInt(Integer::intValue)
                .toArray();
            var medSt = medianInt(stages);
            return fromVisibilityStage(medSt);
        }
        Arrays.parallelSort(zArr);
        var medZ = medianSorted(zArr);
        return fromModifiedZ(medZ);
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
        var rk = row.getRankPosition() != null ? row.getRankPosition() : 0;
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
            + "\n言及順位相当: "
            + rk
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
