package com.geo.analytics.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.geo.analytics.domain.model.BrandMentionMetrics;
import java.util.List;
import org.junit.jupiter.api.Test;

/** #59: 一致規則（繰り返しは数える・同じ箇所は二重に数えない・語の途中に一致しない）を辞書なしで固定する。 */
class BrandMentionMatcherTest {

    /** 形態素の並びから本文と境界配列を組み立てる。 */
    private record Tokenized(String text, boolean[] boundary, int tokenCount) {
        static Tokenized of(String... tokens) {
            String text = String.join("", tokens);
            boolean[] boundary = new boolean[text.length() + 1];
            int offset = 0;
            for (String t : tokens) {
                boundary[offset] = true;
                offset += t.length();
                boundary[offset] = true;
            }
            return new Tokenized(text, boundary, tokens.length);
        }
    }

    private static BrandMentionMetrics count(Tokenized t, String... patternsLongestFirst) {
        return BrandMentionMatcher.count(t.text(), t.boundary(), List.of(patternsLongestFirst), t.tokenCount());
    }

    @Test
    void 繰り返しは強調として回数を数える() {
        var t = Tokenized.of("freee", "は", "人気", "。", "freee", "の", "自動", "仕訳");

        var m = count(t, "freee");

        assertThat(m.mentionCount()).isEqualTo(2);
        assertThat(m.mentionChars()).isEqualTo(10);
    }

    @Test
    void 最長一致を優先し同じ箇所を二重に数えない() {
        var t = Tokenized.of("freee", "会計", "と", "freee");

        var m = count(t, "freee会計", "freee");

        assertThat(m.mentionCount()).isEqualTo(2);
        assertThat(m.mentionChars()).isEqualTo("freee会計".length() + "freee".length());
    }

    @Test
    void 語の途中には一致しない() {
        var t = Tokenized.of("ガストロノミー", "と", "ガスト");

        assertThat(count(t, "ガスト").mentionCount()).isEqualTo(1);
    }

    @Test
    void 終端が境界でなければ一致しない() {
        var t = Tokenized.of("freeee", "は", "別物");

        assertThat(count(t, "freee").mentionCount()).isZero();
    }

    @Test
    void パターンが無ければ0件でトークン数は保持する() {
        var t = Tokenized.of("freee", "は", "人気");

        var m = BrandMentionMatcher.count(t.text(), t.boundary(), List.of(), t.tokenCount());

        assertThat(m.mentionCount()).isZero();
        assertThat(m.totalTokens()).isEqualTo(3);
    }

    @Test
    void 本文が空なら0件() {
        var m = BrandMentionMatcher.count("", new boolean[1], List.of("freee"), 0);

        assertThat(m).isEqualTo(new BrandMentionMetrics(0, 0, 0));
    }

    @Test
    void 密度は回数をトークン数で割った値でトークン0なら0() {
        assertThat(new BrandMentionMetrics(2, 10, 10).density()).isEqualTo(0.2);
        assertThat(new BrandMentionMetrics(0, 0, 0).density()).isZero();
    }
}
