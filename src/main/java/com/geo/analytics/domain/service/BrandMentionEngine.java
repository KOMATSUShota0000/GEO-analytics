package com.geo.analytics.domain.service;

import com.geo.analytics.domain.model.BrandMentionMetrics;
import java.text.Normalizer;
import java.util.LinkedHashSet;
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
 * <p>言及量の接続は #60、引用順位（{@link #citationPosition}）の接続は #66 で行う。
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

    /**
     * 回答本文で対象ブランドが何番目に名前を出されたかを返す（#66）。登場しなければ 0。
     *
     * <p>Why: 引用順位は SoM の4割を占めるのに、LLM の自己申告で常に 0 だった。旧定義は「明示的な順位付き
     * リストの中での順位」に限っていたため、文章形式の回答がすべて 0 になっていた。オーナー確定（2026-09-19）
     * により、番号付きリストでも文章でも**本文に登場する順番**を順位とする。
     *
     * <p>比較対象のブランド名は LLM が回答文から挙げたもの（名前は LLM、数と順序は Java）。同じ表記の重複は
     * 1つとして扱い、自社と同じ表記は除く。
     *
     * @param otherBrandNames 回答文に登場した他ブランドの表記。null や空文字は無視する
     */
    public int citationPosition(String answerText, String brandName, List<String> otherBrandNames) {
        String text = normalize(answerText);
        String self = normalize(brandName);
        if (text.isEmpty() || self.isEmpty()) {
            return 0;
        }
        JapaneseNlpService.TokenBoundaries tokens = japaneseNlpService.tokenBoundaries(text);
        int selfIndex = BrandMentionMatcher.firstIndex(text, tokens.boundary(), self);
        if (selfIndex < 0) {
            return 0;
        }
        var seen = new LinkedHashSet<String>();
        if (otherBrandNames != null) {
            for (String other : otherBrandNames) {
                String pattern = normalize(other);
                if (!pattern.isEmpty() && !pattern.equals(self)) {
                    seen.add(pattern);
                }
            }
        }
        int rank = 1;
        for (String pattern : seen) {
            int index = BrandMentionMatcher.firstIndex(text, tokens.boundary(), pattern);
            if (index >= 0 && index < selfIndex) {
                rank++;
            }
        }
        return rank;
    }

    /** 境界配列は正規化後の文字列に対して作るため、本文とパターンは必ず同じ規則で正規化すること。 */
    static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).strip();
    }
}
