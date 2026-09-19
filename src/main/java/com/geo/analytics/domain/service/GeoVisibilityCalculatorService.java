package com.geo.analytics.domain.service;

import com.geo.analytics.domain.enums.BusinessModelType;
import com.geo.analytics.domain.matching.RobustAuditMathUtil;
import com.geo.analytics.domain.model.SomRawMetrics;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GeoVisibilityCalculatorService {
    private static final Logger log = LoggerFactory.getLogger(GeoVisibilityCalculatorService.class);

    /** Must fit DB column varchar(32) on audit_history.calculation_version. */
    public static final String CALCULATION_VERSION = "V13_GEO4AXIS";

    /**
     * 材料を実測の AI Overview（取れない場合はその推定）へ切り替えた版（ADR-039 / #92）。
     *
     * <p>Why: 同じ式でも材料が変われば別の測定になる。旧版名のままだと、サイト本文を材料にしていた行と
     * 見分けがつかない。材料が実測か推定かの別は audit_histories.material_source に持つ。
     */
    public static final String CALCULATION_VERSION_AIOVERVIEW = "V14_AIOVERVIEW";

    /**
     * 言及密度（言及回数 ÷ 形態素トークン数）がこの値に達すると密度成分が飽和する。
     *
     * <p>Why: 旧値 0.30 は「回答文の3割が自社名」という現実に起こらない基準で、実測（AI Overview 本文20件）の
     * 密度は 0.24%〜4.1%（中央値 0.6%）だった。どれだけ言及されても密度成分がほぼ動かず、スコアが
     * 「1位に出たか」だけで決まっていた。実測の最大値に合わせて 5% とする（#60 / オーナー確定 2026-09-19）。
     */
    private static final double MENTION_DENSITY_SATURATION = 0.05d;

    /**
     * この言及回数に達すると回数成分が飽和する。
     *
     * <p>Why: 旧値 12 は 150〜400字の AI 回答では到達し得ない。実測の言及回数は 0〜5回だったため、
     * 最大値の 5回を満点とする（#60 / オーナー確定 2026-09-19）。
     */
    private static final double MENTION_COUNT_SATURATION = 5.0d;

    /** PWIM（ADR-017）言及成分の重み。単独サイト解析を主用途とするため言及重視に配分。 */
    private static final double PWIM_ALPHA = 0.6d;

    /** PWIM（ADR-017）順位ボーナス成分の重み。 */
    private static final double PWIM_BETA = 0.4d;

    private static final double SOURCE_WEIGHT_HIGH = 1.5;
    private static final double SOURCE_WEIGHT_MEDIUM = 1.0;
    private static final double SOURCE_WEIGHT_LOW = 0.3;

    // GEO Readiness V13_GEO4AXIS の3軸配点（ADR-023）。MEO単独軸を「権威・エンティティ認知」へ昇華。
    /** コンテンツ素地（ルーブリックLLM10基準の合計）の上限。 */
    private static final double MAX_CONTENT = 50.0d;

    /** 技術素地（構造化データ・見出し・llms.txt）の上限。旧25から圧縮（配管シグナルのため軽め）。 */
    private static final double MAX_TECHNICAL = 20.0d;

    /** 権威・エンティティ認知の上限。第三者言及の広がりが中核（Sprint2で本配線）。Sprint1は暫定でMEO由来。 */
    private static final double MAX_AUTHORITY = 30.0d;

    /** ルーブリック機械可読性シグナルの素点上限（RubricCriterionId.MACHINE_READABILITY_SIGNAL）。 */
    private static final double RAW_MACHINE_READABILITY_MAX = 25.0d;

    /** ルーブリックMEOトラストの素点上限。権威軸のローカル向けサブ指標として供給。 */
    private static final double RAW_MEO_MAX = 25.0d;

    /** ローカル業種のみ権威軸へ加点する MEO(クチコミ)サブ指標の上限。第三者中核20＋ローカルサブ10で④軸30を構成。 */
    private static final double MAX_MEO_LOCAL_SUB = 10.0d;

    public static double calculateFinalGeoScore(
            double contentScore, double machineReadabilityScore, double authorityScore) {
        // V13_GEO4AXIS: コンテンツ50＋技術20＋権威30＝100。権威軸を全業種で常時適用するため天井は
        // 常に100で固定でき、軸欠落時の正規化分岐（旧 ADR-019/Sprint1 の mode 依存）は不要になった。
        double content = clamp(contentScore, 0.0d, MAX_CONTENT);
        double technical = technicalSubScore(machineReadabilityScore);
        double authority = clamp(authorityScore, 0.0d, MAX_AUTHORITY);
        return clampPercent(content + technical + authority);
    }

    /**
     * 技術素地(0-20)を算出する。機械可読性の素点(0-25)を配点(0-20)へ線形圧縮する
     * （構造化データ等は配管シグナルのため軽め）。レポート内訳の露出（Sprint4a-1）にも用いる。
     */
    public static double technicalSubScore(double machineReadabilityRaw) {
        return clamp(machineReadabilityRaw, 0.0d, RAW_MACHINE_READABILITY_MAX)
                * (MAX_TECHNICAL / RAW_MACHINE_READABILITY_MAX);
    }

    /**
     * 権威軸の中核＝第三者言及の広がり。地域業種は 0-20（残り 0-10 はローカルMEOサブが担う）。
     * 非地域業種(CORPORATE/ONLINE)は MEOサブを構造的に持たない（Googleマップに実体がなくAIも参照しない）ため、
     * その死蔵枠を中核へ取り戻し 0-{@link #MAX_AUTHORITY} へ線形拡張する（同一カバレッジで地域の1.5倍）。
     * GEO上、非地域は「第三者サイトでの言及」こそが権威の全てである、という因果に基づく。
     * 素点(0-20)自体は業種非依存（DB保存の THIRD_PARTY_MENTIONS は不変＝後方互換）で、業種差はこの集約段階のみで生む。
     * combineAuthority と内訳露出の単一ソース。
     */
    public static double authorityThirdPartyCore(double thirdPartyCore, BusinessModelType mode) {
        if (isNonLocalMode(mode)) {
            double scaled = thirdPartyCore * (MAX_AUTHORITY / ThirdPartyMentionScorer.MAX_AUTHORITY_CORE);
            return clamp(scaled, 0.0d, MAX_AUTHORITY);
        }
        return clamp(thirdPartyCore, 0.0d, ThirdPartyMentionScorer.MAX_AUTHORITY_CORE);
    }

    /** 権威軸のローカルMEOサブ指標(0-10)。非地域業種は MEO を持たないため0。combineAuthority と内訳露出の単一ソース。 */
    public static double authorityLocalMeoSub(double meoRaw, BusinessModelType mode) {
        return isNonLocalMode(mode)
                ? 0.0d
                : clamp(meoRaw, 0.0d, RAW_MEO_MAX) * (MAX_MEO_LOCAL_SUB / RAW_MEO_MAX);
    }

    /**
     * 権威・エンティティ認知(0-30)を合成する。中核＝第三者言及の広がり(0-20)。ローカル業種のみ MEO(クチコミ)を
     * サブ指標として 0-10 加点する。非地域業種は MEO を持たないため中核のみ（Wikipedia/KG ボーナスは別途）。
     */
    public static double combineAuthority(
            double thirdPartyCore, double meoRaw, BusinessModelType mode) {
        return clamp(
                authorityThirdPartyCore(thirdPartyCore, mode) + authorityLocalMeoSub(meoRaw, mode),
                0.0d,
                MAX_AUTHORITY);
    }

    private static boolean isNonLocalMode(BusinessModelType mode) {
        return mode == BusinessModelType.CORPORATE_SERVICE
                || mode == BusinessModelType.ONLINE_SERVICE;
    }

    public static GbvsResult compute(SomRawMetrics metrics, double lAvgJob) {
        return computeBatch(List.of(metrics), lAvgJob).getFirst();
    }

    public static List<GbvsResult> computeBatch(List<SomRawMetrics> rows, double lAvgJob) {
        int n = rows.size();
        if (n == 0) {
            return List.of();
        }
        double[] raw = new double[n];
        IntStream.range(0, n).forEach(i -> raw[i] = weightedSom(rows.get(i)));
        double[] work = Arrays.copyOf(raw, n);
        sanitizeWorkForRobustStatistics(work);
        double[] zScores = RobustAuditMathUtil.modifiedZScores(work);
        if (n == 1) {
            double pct = Math.fma(clamp01(work[0]), 100.0, 0.0);
            return List.of(new GbvsResult(clampPercent(pct), stageFromScorePercent(pct), zScores[0]));
        }
        GbvsResult[] out = new GbvsResult[n];
        IntStream.range(0, n).forEach(i -> {
            double z = zScores[i];
            int stage = stageFromModifiedZ(z);
            // PWIM は絶対評価。ジョブ内 min-max 正規化（相対）を廃し、各クエリのスコアをそのまま百分率化する。
            double pct = clampPercent(Math.fma(100.0, work[i], 0.0));
            out[i] = new GbvsResult(pct, stage, z);
        });
        return IntStream.range(0, n).mapToObj(i -> out[i]).toList();
    }

    private static void sanitizeWorkForRobustStatistics(double[] work) {
        for (int i = 0; i < work.length; i++) {
            if (!Double.isFinite(work[i])) {
                work[i] = 0.0;
            }
        }
    }

    public static double sourceWeightFromUrl(String url) {
        if (url == null || url.isBlank()) {
            return SOURCE_WEIGHT_LOW;
        }
        var normalized = url.strip().toLowerCase();
        if (!normalized.contains("://")) {
            if (normalized.startsWith("prtimes.jp") || normalized.startsWith("www.prtimes.jp")) {
                return SOURCE_WEIGHT_HIGH;
            }
            if (normalized.startsWith("detail.chiebukuro.yahoo.co.jp")) {
                return SOURCE_WEIGHT_MEDIUM;
            }
            return SOURCE_WEIGHT_LOW;
        }
        try {
            var host = java.net.URI.create(normalized).getHost();
            if (host == null || host.isBlank()) {
                return SOURCE_WEIGHT_LOW;
            }
            var h = host.toLowerCase();
            if (h.equals("prtimes.jp") || h.endsWith(".prtimes.jp")) {
                return SOURCE_WEIGHT_HIGH;
            }
            if (h.equals("detail.chiebukuro.yahoo.co.jp")) {
                return SOURCE_WEIGHT_MEDIUM;
            }
            return SOURCE_WEIGHT_LOW;
        } catch (Exception ignored) {
            return SOURCE_WEIGHT_LOW;
        }
    }

    /**
     * 回答文から Java が実測した言及回数・言及文字数・トークン数（#59 / #60）と、引用順位（#66）から SOM を構成する。
     * 感情強度は乗算せず、独立した評判スコアとして扱う（#62 でオーナー確定）。原文値はログにのみ残す。
     */
    private static double weightedSom(SomRawMetrics metrics) {
        Integer aiPos = metrics.aiCitationPosition();
        if (!metrics.isSemanticallyMentioned()) {
            log.info(
                    "[MATH DEBUG] outcome=not_semantic aiPos={} mentions={} mentionChars={} responseTokenLength={} sentimentIntensity={} somScore=0.0",
                    aiPos,
                    metrics.nounCount(),
                    metrics.tokenCount(),
                    metrics.responseTokenLength(),
                    metrics.sentimentIntensity());
            return 0.0;
        }
        int mentions = Math.max(0, metrics.nounCount());
        int total = Math.max(0, metrics.responseTokenLength());
        double presence = (mentions > 0 || aiPos != null) ? 1.0 : 0.0;
        if (presence <= 0.0) {
            log.info(
                    "[MATH DEBUG] outcome=no_presence aiPos={} mentions={} total={} somScore=0.0",
                    aiPos,
                    mentions,
                    total);
            return 0.0;
        }
        // Why: 言及回数 ÷ 形態素トークン数。どちらも Java の実測値で単位が揃っている（#60）。
        //      旧実装は LLM 申告の「文字数」を回数として割っており、意味のない比になっていた。
        double mentionDensity = total > 0 ? (double) mentions / (double) total : 0.0;
        double densityFactor = Math.min(1.0, mentionDensity / MENTION_DENSITY_SATURATION);
        double countFactor = Math.min(1.0, (double) mentions / MENTION_COUNT_SATURATION);
        double mentionSignal = clamp01(0.55 * densityFactor + 0.45 * countFactor);
        if (mentions == 0 && aiPos != null) {
            mentionSignal = Math.max(mentionSignal, 0.12);
        }
        // PWIM（ADR-017）: 順位への乗算依存を廃し、言及=基礎点・順位=加算ボーナスの線形統合へ。
        // 単独サイト解析で aiCitationPosition が空でも、言及があれば非ゼロのスコアを返す（SoMゼロ問題の解消）。
        // Why: 引用元ドメインの重み（sourceWeight）を SoM から外した（ADR-039 の決定5 / #60）。第三者からの
        //      信頼は「AI 回答に出やすくなる原因」で権威軸が担当し、SoM は「AI 回答での見え方という結果」を測る。
        //      これにより言及成分の天井が 0.12 から 0.60 へ戻る。
        double mentionComponent = mentionSignal;
        // 順位ボーナスは NDCG 由来の対数減衰（1位=1.0, 2位≈0.63, 5位≈0.39）。
        double citationBonus = (aiPos != null && aiPos > 0)
                ? clamp01(1.0 / (StrictMath.log(aiPos + 1.0) / StrictMath.log(2.0)))
                : 0.0;
        double sentimentObserved = metrics.sentimentIntensity();
        double pwimScore = clamp01(Math.fma(PWIM_ALPHA, mentionComponent, PWIM_BETA * citationBonus));
        log.info(
                "[MATH DEBUG] outcome=ok model=pwim aiPos={} mentions={} total={} density={} mentionSignal={} mentionComponent={} citationBonus={} alpha={} beta={} sentimentIntensity_observed={} somScore={}",
                aiPos,
                mentions,
                total,
                mentionDensity,
                mentionSignal,
                mentionComponent,
                citationBonus,
                PWIM_ALPHA,
                PWIM_BETA,
                sentimentObserved,
                pwimScore);
        return pwimScore;
    }

    private static double clamp01(double v) {
        return clamp(v, 0.0, 1.0);
    }

    private static double clampPercent(double v) {
        return clamp(v, 0.0, 100.0);
    }

    private static double clamp(double v, double lo, double hi) {
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            return lo;
        }
        return Math.max(lo, Math.min(hi, v));
    }

    private static int stageFromScorePercent(double pct) {
        int inner;
        if (pct >= 90.0) {
            inner = 10;
        } else if (pct >= 80.0) {
            inner = 9;
        } else if (pct >= 70.0) {
            inner = 8;
        } else if (pct >= 60.0) {
            inner = 7;
        } else if (pct >= 50.0) {
            inner = 6;
        } else if (pct >= 40.0) {
            inner = 5;
        } else if (pct >= 30.0) {
            inner = 4;
        } else if (pct >= 20.0) {
            inner = 3;
        } else if (pct >= 10.0) {
            inner = 2;
        } else {
            inner = 1;
        }
        return Math.subtractExact(11, inner);
    }

    private static int stageFromModifiedZ(double z) {
        if (Double.isNaN(z) || Double.isInfinite(z)) {
            return 6;
        }
        int inner;
        if (z >= 3.0) {
            inner = 10;
        } else if (z <= -3.0) {
            inner = 1;
        } else if (z >= 2.25) {
            inner = 9;
        } else if (z >= 1.5) {
            inner = 8;
        } else if (z >= 0.75) {
            inner = 7;
        } else if (z >= 0.0) {
            inner = 6;
        } else if (z >= -0.75) {
            inner = 5;
        } else if (z >= -1.5) {
            inner = 4;
        } else if (z >= -2.25) {
            inner = 3;
        } else {
            inner = 2;
        }
        return Math.subtractExact(11, inner);
    }

    public record GbvsResult(double scorePercent, int visibilityStage, double modifiedZScore) {}
}
