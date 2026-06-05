package com.geo.analytics.domain.service;

import com.geo.analytics.domain.enums.AiRecognitionState;
import java.util.Locale;

/**
 * SoM測定済みのLLM応答から「AIがブランドを正しい実体として認識しているか」を判定する純粋ロジック
 * （V13_GEO4AXIS / Sprint3）。追加API呼び出しはせず、既に得られている応答由来のシグナルだけで分類する。
 *
 * <p>入力は (1) AIがブランドに言及したか、(2) AIが言及した実体を {@code EntityNormalizer} で正規化した
 * ラベル、(3) 顧客の正規ブランド名、の3つ。{@link AiRecognitionState} は<strong>スコア非算入</strong>の
 * 定性エビデンスであり、本クラスの出力をスコア計算へ渡してはならない（SoM との二重計上回避）。
 */
public final class AiRecognitionClassifier {

    /** 正規化後ラベルの部分一致で同一実体とみなす最小文字数。短すぎる偶然一致での誤判定を防ぐ。 */
    private static final int MIN_CONTAINMENT_LENGTH = 2;

    private AiRecognitionClassifier() {}

    /**
     * AIの認識状態を分類する。
     *
     * @param brandMentioned       AIが応答内で当該ブランドに言及したか
     * @param resolvedEntityLabel  AIが言及した実体を正規化したラベル（{@code EntityNormalizer} の解決結果）
     * @param canonicalBrand       顧客の正規ブランド名（判定の基準）
     */
    public static AiRecognitionState classify(boolean brandMentioned, String resolvedEntityLabel, String canonicalBrand) {
        // 言及がない／実体を解決できない／基準が無い場合は「認識していない」と扱う（取り違えと断定しない）。
        if (!brandMentioned || isBlank(resolvedEntityLabel) || isBlank(canonicalBrand)) {
            return AiRecognitionState.UNKNOWN;
        }
        return matchesSameEntity(resolvedEntityLabel, canonicalBrand)
                ? AiRecognitionState.RECOGNIZED_CORRECTLY
                : AiRecognitionState.MISIDENTIFIED;
    }

    /**
     * 2つのラベルが同一実体を指すかを正規化のうえ判定する。法人格表記（株式会社等）や空白の揺れを吸収し、
     * 完全一致に加えて十分な長さの包含関係（「アクメ」⊂「アクメ株式会社」等）も同一とみなす。
     */
    private static boolean matchesSameEntity(String a, String b) {
        String na = normalize(a);
        String nb = normalize(b);
        if (na.isEmpty() || nb.isEmpty()) {
            return false;
        }
        if (na.equals(nb)) {
            return true;
        }
        int shorter = StrictMath.min(na.length(), nb.length());
        if (shorter < MIN_CONTAINMENT_LENGTH) {
            return false;
        }
        return na.contains(nb) || nb.contains(na);
    }

    /** 小文字化・空白除去・代表的な法人格表記の除去で表記揺れを正規化する。 */
    private static String normalize(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (!Character.isWhitespace(c)) {
                sb.append(c);
            }
        }
        String compact = sb.toString();
        for (String legalForm : LEGAL_FORMS) {
            compact = compact.replace(legalForm, "");
        }
        return compact;
    }

    /** 正規化時に除去する法人格・組織種別の表記（小文字・空白除去後の形に合わせる）。 */
    private static final String[] LEGAL_FORMS = {
        "株式会社", "有限会社", "合同会社", "（株）", "(株)", "㈱", "（有）", "(有)", "㈲",
        "co.,ltd.", "co.,ltd", "co.ltd", "ltd.", "ltd", "inc.", "inc", "llc", "corporation", "corp."
    };

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
