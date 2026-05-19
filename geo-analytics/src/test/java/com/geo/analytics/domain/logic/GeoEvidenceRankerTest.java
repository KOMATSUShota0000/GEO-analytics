package com.geo.analytics.domain.logic;

import static org.assertj.core.api.Assertions.assertThat;

import com.geo.analytics.domain.model.GeoRagEvidence;
import com.geo.analytics.domain.model.GeoEvidenceRow;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GeoEvidenceRankerTest {

    private final KeywordSimilarityScorer scorer = new KeywordSimilarityScorer();

    @Test
    void nullOrEmptyRowsReturnsEmpty() {
        var p = new GeoEvidenceRanker(scorer);
        assertThat(p.provideEvidences("q", null, 5, 2, "cat")).isEmpty();
        assertThat(p.provideEvidences("q", List.of(), 5, 2, "cat")).isEmpty();
        assertThat(p.provideEvidences("q", List.of(sample()), 0, 2, "cat")).isEmpty();
    }

    @Test
    void atMostNEvidences() {
        var p = new GeoEvidenceRanker(scorer);
        List<GeoEvidenceRow> rows = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            rows.add(
                    new GeoEvidenceRow(
                            "https://site-" + i + ".example/p",
                            "title " + i + " keyword",
                            "snippet keyword " + i,
                            Optional.empty(),
                            Optional.empty()));
        }
        List<GeoRagEvidence> out = p.provideEvidences("keyword", rows, 3, 2, "GEO");
        assertThat(out).hasSizeLessThanOrEqualTo(3);
    }

    @Test
    void domainCapLimitsSameHost() {
        SimilarityScorer uniform = (q, c) -> 1.0d;
        var p = new GeoEvidenceRanker(uniform, 1.0d, 0.0d, 10, 0.99d);
        Instant t = Instant.parse("2024-01-01T00:00:00Z");
        List<GeoEvidenceRow> rows =
                List.of(
                        new GeoEvidenceRow(
                                "https://dup.example/a",
                                "a",
                                "x",
                                Optional.of(t),
                                Optional.empty()),
                        new GeoEvidenceRow(
                                "https://dup.example/b",
                                "b",
                                "y",
                                Optional.of(t),
                                Optional.empty()),
                        new GeoEvidenceRow(
                                "https://dup.example/c",
                                "c",
                                "z",
                                Optional.of(t),
                                Optional.empty()),
                        new GeoEvidenceRow(
                                "https://other.example/o",
                                "o",
                                "w",
                                Optional.of(t),
                                Optional.empty()));
        List<GeoRagEvidence> out = p.provideEvidences("q", rows, 10, 2, "c", Instant.parse("2025-01-01T00:00:00Z"));
        long dupHost =
                out.stream()
                        .filter(e -> e.url().contains("dup.example"))
                        .count();
        assertThat(dupHost).isLessThanOrEqualTo(2);
        assertThat(out.stream().anyMatch(e -> e.url().contains("other.example"))).isTrue();
    }

    @Test
    void unknownDateUsesMaxDecayRankingVersusFresh() {
        SimilarityScorer uniform = (q, c) -> 1.0d;
        var p = new GeoEvidenceRanker(uniform, 1.0d, 0.5d, 10, 0.99d);
        Instant ref = Instant.parse("2025-06-01T00:00:00Z");
        Instant fresh = Instant.parse("2025-05-01T00:00:00Z");
        List<GeoEvidenceRow> rows =
                List.of(
                        new GeoEvidenceRow(
                                "https://old.example/u",
                                "t",
                                "samecontent",
                                Optional.empty(),
                                Optional.empty()),
                        new GeoEvidenceRow(
                                "https://new.example/u",
                                "t",
                                "samecontent",
                                Optional.of(fresh),
                                Optional.empty()));
        List<GeoRagEvidence> out = p.provideEvidences("x", rows, 1, 2, "c", ref);
        assertThat(out).hasSize(1);
        assertThat(out.get(0).url()).contains("new.example");
    }

    @Test
    void nearDuplicateSnippetDropsLowerPriority() {
        SimilarityScorer uniform = (q, c) -> 1.0d;
        var p = new GeoEvidenceRanker(uniform, 1.0d, 0.0d, 10, 0.5d);
        Instant t = Instant.parse("2024-01-01T00:00:00Z");
        String same = "ほぼ同一のスニペット本文で繰り返しテキスト";
        List<GeoEvidenceRow> rows =
                List.of(
                        new GeoEvidenceRow("https://a.example/1", "タイトル", same, Optional.of(t), Optional.empty()),
                        new GeoEvidenceRow("https://b.example/2", "タイトル", same, Optional.of(t), Optional.empty()));
        List<GeoRagEvidence> out = p.provideEvidences("q", rows, 5, 2, "c", Instant.parse("2025-01-01T00:00:00Z"));
        assertThat(out).hasSize(1);
    }

    @Test
    void keywordScorerReturnsBounded() {
        KeywordSimilarityScorer k = new KeywordSimilarityScorer();
        assertThat(k.score(null, null)).isBetween(0.0d, 1.0d);
        assertThat(k.score("abc", "abc")).isBetween(0.0d, 1.0d);
    }

    @Test
    void hostKeyExtractsHost() {
        assertThat(GeoEvidenceRanker.hostKey("https://WWW.EXAMPLE.COM/path")).isEqualTo("www.example.com");
    }

    private static GeoEvidenceRow sample() {
        return new GeoEvidenceRow(
                "https://x.example/p", "t", "s", Optional.empty(), Optional.empty());
    }
}
