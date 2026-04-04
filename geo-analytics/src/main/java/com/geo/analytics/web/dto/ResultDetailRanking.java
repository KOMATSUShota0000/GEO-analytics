package com.geo.analytics.web.dto;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ResultDetailRanking {

    private ResultDetailRanking() {
    }

    public static List<ResultDetailResponse> withGbvsCompetitionRanks(List<ResultDetailResponse> rows) {
        if (rows == null || rows.isEmpty()) {
            return rows == null ? List.of() : rows;
        }
        int n = rows.size();
        record Item(int idx, ResultDetailResponse r, double key) {
        }
        List<Item> items = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            ResultDetailResponse r = rows.get(i);
            Double g = r.gbvsNormalizedScore();
            if (g == null) {
                g = r.somScore();
            }
            double key;
            if (g == null || g.isNaN()) {
                key = Double.NEGATIVE_INFINITY;
            } else {
                key = g;
            }
            items.add(new Item(i, r, key));
        }
        items.sort(Comparator
                .comparingDouble(Item::key).reversed()
                .thenComparing(it -> it.r().query(), Comparator.nullsLast(String::compareTo)));
        int[] rankAt = new int[n];
        int p = 0;
        while (p < n) {
            int runStart = p;
            double k = items.get(p).key;
            while (p < n && Double.compare(items.get(p).key, k) == 0) {
                p++;
            }
            int rank = runStart + 1;
            for (int j = runStart; j < p; j++) {
                rankAt[items.get(j).idx] = rank;
            }
        }
        List<ResultDetailResponse> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(rows.get(i).withMentionRank(rankAt[i]));
        }
        return List.copyOf(out);
    }
}
