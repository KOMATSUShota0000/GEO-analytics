package com.geo.analytics.domain.service;

import com.geo.analytics.domain.model.BrandMentionMetrics;
import java.util.List;

/**
 * 正規化済み本文と形態素境界から、ブランド表記の出現を数える純粋ロジック。Sudachi に依存しない。
 *
 * <p>一致規則（#59 でオーナー合意済み）:
 * <ul>
 *   <li>繰り返しは強調として数える（2回出れば2回）</li>
 *   <li>同じ1箇所を2回数えないため、最長一致を優先し、一致した範囲を飛ばして次を探す</li>
 *   <li>一致は形態素の境界で始まり境界で終わるものに限る（「ガスト」が「ガストロノミー」の一部に誤一致しないため）</li>
 * </ul>
 */
final class BrandMentionMatcher {

    private BrandMentionMatcher() {}

    /**
     * @param normalizedText    正規化済みの本文（境界配列はこの文字列の添字に対応する）
     * @param boundary          長さ {@code normalizedText.length() + 1}。添字 i で形態素が始まるか終わるなら true
     * @param patternsLongestFirst 正規化済みの表記。長い順に並べ、空文字を含めないこと
     * @param totalTokens       本文の形態素トークン数
     */
    static BrandMentionMetrics count(
            String normalizedText, boolean[] boundary, List<String> patternsLongestFirst, int totalTokens) {
        int length = normalizedText.length();
        if (length == 0 || patternsLongestFirst.isEmpty()) {
            return new BrandMentionMetrics(0, 0, totalTokens);
        }
        int mentions = 0;
        int chars = 0;
        int position = 0;
        int patternCount = patternsLongestFirst.size();
        while (position < length) {
            int matchedLength = 0;
            if (boundary[position]) {
                for (int p = 0; p < patternCount; p++) {
                    String pattern = patternsLongestFirst.get(p);
                    int end = position + pattern.length();
                    if (end <= length
                            && boundary[end]
                            && normalizedText.regionMatches(position, pattern, 0, pattern.length())) {
                        matchedLength = pattern.length();
                        break;
                    }
                }
            }
            if (matchedLength > 0) {
                mentions++;
                chars += matchedLength;
                position += matchedLength;
            } else {
                position++;
            }
        }
        return new BrandMentionMetrics(mentions, chars, totalTokens);
    }
}
