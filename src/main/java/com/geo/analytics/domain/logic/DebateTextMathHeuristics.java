package com.geo.analytics.domain.logic;

import com.geo.analytics.domain.ai.CitationValidator;
import java.lang.StrictMath;
import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * ディベート各ラウンドのエージェント本文から、数理カーネル接続用の軽量ヒューリスティック特徴を導出する。ベクトルDB
 * 不要。
 */
public final class DebateTextMathHeuristics {

    public static final double[] P_MARKET_UNIFORM_4 = {0.25d, 0.25d, 0.25d, 0.25d};

    private static final double MASS_FLOOR = 1.0e-9d;
    private static final int MAX_S_DENSITY_CHARS = 100_000;

    private static final String[] KEYWORDS_FACTS =
            new String[] {
                "年", "月", "日", "%", "％", "円", "人", "件", "第", "倍", "割",
            };
    private static final String[] KEYWORDS_PRODUCT =
            new String[] {
                "サービス", "製品", "機能", "特徴", "ソリューション", "提供", "料金", "API", "システム", "プラットフォーム"
            };
    private static final String[] KEYWORDS_AUDIENCE =
            new String[] {
                "お客様", "顧客", "ユーザー", "導入", "ご案内", "採用", "比較", "向け", "あなた", "お困り", "予約", "相談"
            };
    private static final String[] KEYWORDS_INTENT =
            new String[] {
                "導入", "比較", "課題", "解決", "選ん", "理由", "特徴", "なぜ", "申込", "相談", "予約", "最適", "診断"
            };
    private static final String[] KEYWORDS_SKEPTIC_TONE =
            new String[] {
                "飛躍", "ふわ", "不十分", "一般論", "同業", "欠如", "曖昧", "危険", "特に"
            };
    private static final Pattern LINE_BULLET = Pattern.compile("(?m)^\\s*([\\-–・*]|[0-9]+[.)])\\s*");

    private DebateTextMathHeuristics() {}

    /**
     * 1 ラウンド分の原文・各エージェント文から、分布・スカラー・質量・収束用配列をまとめて導出する。
     */
    public static RoundHeuristicResult compute(
            String pagePlainText, String analystText, String innovatorText, String skepticText) {
        String a = safe(analystText);
        String n = safe(innovatorText);
        String s = safe(skepticText);
        String page = safe(pagePlainText);
        String combined = a + "\n" + n + "\n" + s;

        double rawDigitScore = (double) countDigits(page + combined) * 0.5d
                + (double) countOccurrences(page + combined, KEYWORDS_FACTS) * 1.2d
                + 1.0d;
        double rawProductScore = (double) countOccurrences(combined, KEYWORDS_PRODUCT) * 1.5d + 1.0d;
        double rawAudienceScore = (double) countOccurrences(combined, KEYWORDS_AUDIENCE) * 1.4d + 1.0d;
        int len = page.length() + combined.length();
        if (len > MAX_S_DENSITY_CHARS) {
            len = MAX_S_DENSITY_CHARS;
        }
        double otherMass = MASS_FLOOR + StrictMath.max(1.0d, (double) len * 0.01d) - 0.05d * (rawDigitScore + rawProductScore + rawAudienceScore);
        if (otherMass < MASS_FLOOR) {
            otherMass = MASS_FLOOR;
        }
        double[] pSite = normalize4Mass(rawDigitScore, rawProductScore, rawAudienceScore, otherMass);

        double sDensity = clampNonNegFinite((double) len / 200.0d);
        if (sDensity > 1.0e6d) {
            sDensity = 1.0e6d;
        }
        int intentHits = countOccurrences(page + combined, KEYWORDS_INTENT);
        double qIntent = Math.min(1.0d, (double) intentHits / 18.0d);

        double[] agentMass = new double[3];
        agentMass[0] = clamp01(0.22d + 0.12d * (double) countBulletLines(a) + 0.01d * (double) countDigits(a));
        int citeCount = CitationValidator.extractCitations(n).size();
        boolean cited = CitationValidator.hasValidCitation(n);
        agentMass[1] = clamp01((cited ? 0.58d : 0.32d) + 0.06d * (double) citeCount);
        agentMass[2] = clamp01(0.2d + 0.1d * (double) countOccurrences(s, KEYWORDS_SKEPTIC_TONE));

        double[] currConf = Arrays.copyOf(pSite, 4);
        double[] centroid = threeWayCentroidFromLengths((double) a.length(), (double) n.length(), (double) s.length());

        return new RoundHeuristicResult(pSite, sDensity, qIntent, agentMass, currConf, centroid);
    }

    /**
     * 1 ラウンド分の抽出結果。{@code pSite} は正規化分布、{@code agentMass} は3エージェント、{@code
     * currConfidences} は収束用4次元（本実装では {@code pSite} のコピー）、{@code currCentroid} は3次元
     *（L1 正規化の活性ベクトル）。
     */
    public record RoundHeuristicResult(
            double[] pSite,
            double sDensity,
            double qIntent,
            double[] agentMass,
            double[] currConfidences,
            double[] currCentroid) {}

    private static String safe(String t) {
        return t == null ? "" : t;
    }

    private static double[] normalize4Mass(double a, double b, double c, double d) {
        double s = a + b + c + d;
        if (!Double.isFinite(s) || s <= 0.0d) {
            return new double[] {0.25d, 0.25d, 0.25d, 0.25d};
        }
        return new double[] {a / s, b / s, c / s, d / s};
    }

    private static double[] threeWayCentroidFromLengths(double la, double li, double ls) {
        if (!Double.isFinite(la)) {
            la = 0.0d;
        }
        if (!Double.isFinite(li)) {
            li = 0.0d;
        }
        if (!Double.isFinite(ls)) {
            ls = 0.0d;
        }
        la = Math.max(0.0d, la);
        li = Math.max(0.0d, li);
        ls = Math.max(0.0d, ls);
        double t = la + li + ls;
        if (t <= MASS_FLOOR) {
            return new double[] {1.0d / 3.0d, 1.0d / 3.0d, 1.0d / 3.0d};
        }
        return new double[] {la / t, li / t, ls / t};
    }

    private static int countDigits(String s) {
        int c = 0;
        for (int i = 0; i < s.length(); i++) {
            if (Character.isDigit(s.charAt(i))) {
                c++;
            }
        }
        return c;
    }

    private static int countOccurrences(String haystack, String[] needles) {
        int t = 0;
        for (String n : needles) {
            t += countSub(haystack, n);
        }
        return t;
    }

    private static int countSub(String hay, String n) {
        if (n.isEmpty() || hay.isEmpty()) {
            return 0;
        }
        int c = 0;
        int f = 0;
        while (f >= 0 && f < hay.length()) {
            f = hay.indexOf(n, f);
            if (f < 0) {
                break;
            }
            c++;
            f += n.length();
        }
        return c;
    }

    private static int countBulletLines(String t) {
        if (t.isEmpty()) {
            return 0;
        }
        int c = 0;
        for (int i = 0; i < t.length(); i++) {
            if (t.charAt(i) == '\n') {
                c++;
            }
        }
        int m = 0;
        var matcher = LINE_BULLET.matcher(t);
        while (matcher.find()) {
            m++;
        }
        return m + c / 3;
    }

    private static double clamp01(double v) {
        if (Double.isNaN(v) || v < 0.0d) {
            return 0.0d;
        }
        if (v > 1.0d) {
            return 1.0d;
        }
        return v;
    }

    private static double clampNonNegFinite(double v) {
        if (Double.isNaN(v) || v < 0.0d) {
            return 0.0d;
        }
        if (Double.isInfinite(v)) {
            return 1.0e6d;
        }
        return v;
    }
}
