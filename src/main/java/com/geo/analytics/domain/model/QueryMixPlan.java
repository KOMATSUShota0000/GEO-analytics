package com.geo.analytics.domain.model;

/**
 * 1解析で生成する検索クエリの内訳。指名検索（ブランド名あり）と一般検索（ブランド名なし）の本数を決める。
 *
 * <p>Why: 旧実装は比率を制約しておらず、実測で生成された10クエリの**全部**にブランド名が入っていた。
 * 検索語に自社名が入っていれば AI 回答が自社に言及するのは当然で、測定が自作自演になる。
 * 「まだ知らない人に発見されるか」という GEO の主戦場が測れていなかった（#87）。
 *
 * <p>固定比率（7:3 等）ではなく指名検索を少数で頭打ちにするのは、指名検索が本数を増やしても情報が
 * 増えないため。「ブランド名 とは」で AI が何を語るか分かれば、誤情報・古い情報・競合への誘導は
 * 少数で検出できる。一方で一般検索は「どのクエリなら拾われるか」の網羅性そのものが価値なので本数が効く。
 * 比率で切ると STANDARD の3本が破綻する（7:3 なら指名 0.9本）。
 */
public record QueryMixPlan(int brandedCount, int genericCount) {

    /** 指名検索の上限。これ以上増やしても検出できる問題が増えないため。 */
    private static final int MAX_BRANDED = 3;

    /** 総数の何分の1までを指名検索に充てるか。3本なら1本（下限）、10本なら2本、30本なら6本→上限3。 */
    private static final int BRANDED_DENOMINATOR = 5;

    public QueryMixPlan {
        if (brandedCount < 0 || genericCount < 0) {
            throw new IllegalArgumentException("counts must not be negative");
        }
    }

    public static QueryMixPlan forTotal(int totalCount) {
        int total = Math.max(1, totalCount);
        if (total == 1) {
            // Why: 1本しか撃てないなら、改善余地のある一般検索へ回す。
            return new QueryMixPlan(0, 1);
        }
        int branded = Math.min(MAX_BRANDED, Math.max(1, total / BRANDED_DENOMINATOR));
        return new QueryMixPlan(branded, total - branded);
    }

    public int totalCount() {
        return brandedCount + genericCount;
    }
}
