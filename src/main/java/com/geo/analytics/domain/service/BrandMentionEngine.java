package com.geo.analytics.domain.service;

import com.geo.analytics.domain.model.BrandMentionMetrics;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 回答本文におけるブランド言及量を Java で数えるエンジン（#59）。
 *
 * <p>Why: 言及回数・言及箇所の文字数といった確定的な物理量は、これまで LLM の自己申告（token_count）に
 * 頼っていた（.cursorrules 12節違反）。しかも受け取る側の {@code SomRawMetrics.nounCount} に LLM 申告の
 * 文字数が入り、計算式が「文字数 ÷ トークン数」という単位の合わない割り算になっていた（#60）。
 * 本文とブランド名だけから決定論的に数える。
 *
 * <p>表記の揺れは NFKC 正規化と小文字化で吸収する（全角「ＦＲＥＥＥ」、「Freee」）。読みでの一致は行わない。
 * ラテン文字のブランド名が「フリー」等に誤一致する害が大きいため（#59 でオーナー合意済み）。
 *
 * <p>本クラスは既存の計算経路へまだ接続していない。スコア入力の切り替えは #60 で行う。
 */
public final class BrandMentionEngine {

    private final JapaneseNlpService japaneseNlpService;

    public BrandMentionEngine(JapaneseNlpService japaneseNlpService) {
        this.japaneseNlpService = Objects.requireNonNull(japaneseNlpService);
    }

    public BrandMentionMetrics measure(String answerText, String brandName) {
        String text = normalize(answerText);
        if (text.isEmpty()) {
            return BrandMentionMetrics.NONE;
        }
        JapaneseNlpService.TokenBoundaries tokens = japaneseNlpService.tokenBoundaries(text);
        String pattern = normalize(brandName);
        if (pattern.isEmpty()) {
            return new BrandMentionMetrics(0, 0, tokens.tokenCount());
        }
        return BrandMentionMatcher.count(text, tokens.boundary(), List.of(pattern), tokens.tokenCount());
    }

    /** 境界配列は正規化後の文字列に対して作るため、本文とパターンは必ず同じ規則で正規化すること。 */
    static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).strip();
    }
}
