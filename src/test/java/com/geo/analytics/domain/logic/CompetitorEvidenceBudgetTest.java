package com.geo.analytics.domain.logic;

import static org.assertj.core.api.Assertions.assertThat;

import com.geo.analytics.domain.model.GeoRagEvidence;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CompetitorEvidenceBudgetTest {

    private static GeoRagEvidence ev(String url, String title, String snippet) {
        return new GeoRagEvidence(url, title, snippet, 1.0, Optional.empty(), "CAT");
    }

    @Test
    void maxCharsNonPositive_returnsEmpty() {
        assertThat(CompetitorEvidenceBudget.clipEvidences(List.of(ev("u", "t", "s")), 0)).isEmpty();
        assertThat(CompetitorEvidenceBudget.clipEvidences(List.of(ev("u", "t", "s")), -1)).isEmpty();
    }

    @Test
    void nullOrEmptyInput_returnsEmpty() {
        assertThat(CompetitorEvidenceBudget.clipEvidences(null, 100)).isEmpty();
        assertThat(CompetitorEvidenceBudget.clipEvidences(List.of(), 100)).isEmpty();
    }

    @Test
    void withinBudget_returnsAllIncludingNullSkipped() {
        var a = ev("http://a", "A", "snippet-a");
        var b = ev("http://b", "B", "snippet-b");
        ArrayList<GeoRagEvidence> list = new ArrayList<>();
        list.add(a);
        list.add(null);
        list.add(b);
        int len = GeoRagEvidenceXmlBuilder.buildCompetitorBlock(List.of(a, b)).length();
        List<GeoRagEvidence> out = CompetitorEvidenceBudget.clipEvidences(list, len + 100);
        assertThat(out).containsExactly(a, b);
    }

    @Test
    void overBudget_dropsLowerPriorityTail() {
        var high = ev("http://h", "H", "short");
        var low = ev("http://l", "L", "THIS_IS_A_VERY_LONG_SNIPPET_TO_FORCE_OVERFLOW");
        int oneOnlyLen = GeoRagEvidenceXmlBuilder.buildCompetitorBlock(List.of(high)).length();
        int bothLen = GeoRagEvidenceXmlBuilder.buildCompetitorBlock(List.of(high, low)).length();
        assertThat(bothLen).isGreaterThan(oneOnlyLen);
        List<GeoRagEvidence> out = CompetitorEvidenceBudget.clipEvidences(List.of(high, low), oneOnlyLen);
        assertThat(out).hasSize(1);
        assertThat(out.get(0).url()).isEqualTo("http://h");
    }

    @Test
    void singleItemOverBudget_truncatesSnippetWithEllipsis() {
        var e = ev("http://x", "T", "0123456789012345678901234567890123456789");
        int fullLen = GeoRagEvidenceXmlBuilder.buildCompetitorBlock(List.of(e)).length();
        int target = fullLen - 5;
        List<GeoRagEvidence> out = CompetitorEvidenceBudget.clipEvidences(List.of(e), target);
        assertThat(out).hasSize(1);
        assertThat(out.get(0).snippet()).endsWith("...");
        assertThat(GeoRagEvidenceXmlBuilder.buildCompetitorBlock(out).length()).isLessThanOrEqualTo(target);
    }

    @Test
    void surrogateSafe_truncationWithEmojiDoesNotSplitCodePoints() {
        String emoji = "\uD83D\uDE00";
        String longSnippet = "ABC" + emoji + "DEF" + "y".repeat(400);
        var e = ev("http://emoji", "E", longSnippet);
        int fullXmlLen = GeoRagEvidenceXmlBuilder.buildCompetitorBlock(List.of(e)).length();
        int budget = fullXmlLen / 2;
        List<GeoRagEvidence> out = CompetitorEvidenceBudget.clipEvidences(List.of(e), budget);
        assertThat(out).hasSize(1);
        String sn = out.get(0).snippet();
        assertThat(GeoRagEvidenceXmlBuilder.buildCompetitorBlock(out).length()).isLessThanOrEqualTo(budget);
        int i = 0;
        while (i < sn.length()) {
            int cp = sn.codePointAt(i);
            i += Character.charCount(cp);
        }
        assertThat(i).isEqualTo(sn.length());
    }
}
