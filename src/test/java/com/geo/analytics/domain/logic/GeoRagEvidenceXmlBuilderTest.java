package com.geo.analytics.domain.logic;

import static org.assertj.core.api.Assertions.assertThat;

import com.geo.analytics.domain.model.GeoRagEvidence;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GeoRagEvidenceXmlBuilderTest {

    @Test
    void escapeXml_escapesSpecialCharacters() {
        assertThat(GeoRagEvidenceXmlBuilder.escapeXml(null)).isEmpty();
        assertThat(GeoRagEvidenceXmlBuilder.escapeXml("")).isEmpty();
        assertThat(GeoRagEvidenceXmlBuilder.escapeXml("a&b<c>d'e\"f"))
                .isEqualTo("a&amp;b&lt;c&gt;d&apos;e&quot;f");
    }

    @Test
    void buildCompetitorBlock_returnsEmptyForNullOrEmpty() {
        assertThat(GeoRagEvidenceXmlBuilder.buildCompetitorBlock(null)).isEmpty();
        assertThat(GeoRagEvidenceXmlBuilder.buildCompetitorBlock(List.of())).isEmpty();
    }

    @Test
    void buildCompetitorBlock_wrapsEvidenceAndSkipsNullEntries() {
        GeoRagEvidence e1 =
                new GeoRagEvidence(
                        "https://a.com?q=1&x=<y>",
                        "Title <tag>",
                        "Snippet & \"quote\"",
                        1.0,
                        Optional.of(Instant.parse("2024-01-15T00:00:00Z")),
                        "CAT");
        ArrayList<GeoRagEvidence> withNull = new ArrayList<>();
        withNull.add(null);
        withNull.add(e1);
        String xml = GeoRagEvidenceXmlBuilder.buildCompetitorBlock(withNull);
        assertThat(xml)
                .isEqualTo(
                        """
                        <competitor_seo_data>
                          <evidence>
                            <url>https://a.com?q=1&amp;x=&lt;y&gt;</url>
                            <title>Title &lt;tag&gt;</title>
                            <snippet>Snippet &amp; &quot;quote&quot;</snippet>
                          </evidence>
                        </competitor_seo_data>""");
    }
}
