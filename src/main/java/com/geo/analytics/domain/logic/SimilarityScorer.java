package com.geo.analytics.domain.logic;

/**
 * クエリと文書断片の類似度。フェーズ2以降のベクトル検索へ実装差し替え可能。
 *
 * <p>実装は戻り値を {@code [0, 1]} に収めること。
 */
@FunctionalInterface
public interface SimilarityScorer {

    /**
     * @param query 検索クエリ（null は空文字として扱うのが望ましい）
     * @param content 通常は title + 空白 + snippet の連結
     * @return {@code [0, 1]} の類似度。非有限は呼び出し側で 0 扱い推奨
     */
    double score(String query, String content);
}
