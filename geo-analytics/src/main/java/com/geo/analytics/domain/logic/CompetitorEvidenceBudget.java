package com.geo.analytics.domain.logic;

import com.geo.analytics.domain.model.SeoEvidence;
import java.util.ArrayList;
import java.util.List;

/**
 * プランごとの競合エビデンス上限に収まるよう、競合ブロック XML 相当のボリュームを削る。
 */
public final class CompetitorEvidenceBudget {

    private static final String SNIPPET_TAIL_ELLIPSIS = "...";

    private CompetitorEvidenceBudget() {}

    /**
     * {@code sortedEvidences} は優先度が高い順（先頭ほど重要）。{@code maxChars <= 0} のときは空リスト。
     * 元リストは変更しない。
     */
    public static List<SeoEvidence> clipEvidences(List<SeoEvidence> sortedEvidences, int maxChars) {
        if (sortedEvidences == null || sortedEvidences.isEmpty() || maxChars <= 0) {
            return List.of();
        }
        ArrayList<SeoEvidence> work = new ArrayList<>(sortedEvidences.size());
        for (SeoEvidence e : sortedEvidences) {
            if (e != null) {
                work.add(e);
            }
        }
        if (work.isEmpty()) {
            return List.of();
        }
        while (work.size() > 1 && competitorXmlLength(work) > maxChars) {
            work.remove(work.size() - 1);
        }
        if (competitorXmlLength(work) <= maxChars) {
            return List.copyOf(work);
        }
        return fitSingleEvidence(work.get(0), maxChars);
    }

    private static int competitorXmlLength(List<SeoEvidence> evidences) {
        return SeoEvidenceXmlBuilder.buildCompetitorBlock(evidences).length();
    }

    private static List<SeoEvidence> fitSingleEvidence(SeoEvidence e, int maxChars) {
        String full = e.snippet() == null ? "" : e.snippet();
        int totalCp = full.codePointCount(0, full.length());
        for (int prefixCp = totalCp; prefixCp >= 0; prefixCp--) {
            String snippet = snippetPrefixWithEllipsisByCodePoints(full, prefixCp);
            SeoEvidence candidate =
                    new SeoEvidence(
                            e.url(),
                            e.title(),
                            snippet,
                            e.priorityScore(),
                            e.publishedAt(),
                            e.relevanceCategory());
            if (competitorXmlLength(List.of(candidate)) <= maxChars) {
                return List.of(candidate);
            }
        }
        return List.of();
    }

    private static String snippetPrefixWithEllipsisByCodePoints(String full, int prefixCpCount) {
        if (full.isEmpty()) {
            return "";
        }
        int totalCp = full.codePointCount(0, full.length());
        if (prefixCpCount >= totalCp) {
            return full;
        }
        if (prefixCpCount <= 0) {
            return SNIPPET_TAIL_ELLIPSIS;
        }
        int endIndex = full.offsetByCodePoints(0, prefixCpCount);
        return full.substring(0, endIndex) + SNIPPET_TAIL_ELLIPSIS;
    }
}
