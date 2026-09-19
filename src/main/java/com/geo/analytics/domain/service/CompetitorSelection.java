package com.geo.analytics.domain.service;

import java.util.ArrayList;
import java.util.List;

/**
 * LLM が挙げた競合名から、集計に入れるものを選ぶ純粋ロジック（#64 / #65）。
 *
 * <p>Why: 旧実装は LLM の出力をそのまま競合として扱っていたため、(1) 自社が競合として混入する、
 * (2) 「その他」という実在しない残余カテゴリが混ざる、(3) 表記ゆれが別エンティティとして二重計上される、
 * の3つが同時に起きていた（実測でも確認）。名前は LLM、選別と計数は Java という分担にする。
 */
public final class CompetitorSelection {

    private CompetitorSelection() {}

    /**
     * @param rawNames   LLM が挙げた競合名（回答文に登場したもの）
     * @param mainBrand  評価対象のブランド名
     * @param normalizer 名寄せエンジン
     * @return 採用する競合名。LLM が挙げた順序を保つ
     */
    public static List<String> accept(List<String> rawNames, String mainBrand, EntityNormalizer normalizer) {
        List<String> accepted = new ArrayList<>();
        if (rawNames == null || normalizer == null) {
            return accepted;
        }
        for (String rawName : rawNames) {
            String label = rawName == null ? "" : rawName.strip();
            if (label.isEmpty() || EntityNormalizer.isResidualCategory(label)) {
                continue;
            }
            if (!EntityNormalizer.UNMATCHED.equals(normalizer.resolve(label, mainBrand))) {
                continue;
            }
            if (!EntityNormalizer.UNMATCHED.equals(normalizer.resolveAmong(label, accepted))) {
                continue;
            }
            accepted.add(label);
        }
        return List.copyOf(accepted);
    }
}
