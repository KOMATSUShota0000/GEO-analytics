package com.geo.analytics.domain.logic;

import static org.assertj.core.api.Assertions.assertThat;

import com.geo.analytics.domain.model.SeoEvidence;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SeoEvidenceXmlBuilderTest {

    @Test
    void escapeXml_escapesSpecialCharacters() {
        assertThat(SeoEvidenceXmlBuilder.escapeXml(null)).isEmpty();
        assertThat(SeoEvidenceXmlBuilder.escapeXml("")).isEmpty();
        assertThat(SeoEvidenceXmlBuilder.escapeXml("a&b<c>d'e\"f"))
                .isEqualTo("a&amp;b&lt;c&gt;d&apos;e&quot;f");
    }

    @Test
    void buildCompetitorBlock_returnsEmptyForNullOrEmpty() {
        assertThat(SeoEvidenceXmlBuilder.buildCompetitorBlock(null)).isEmpty();
        assertThat(SeoEvidenceXmlBuilder.buildCompetitorBlock(List.of())).isEmpty();
    }

    @Test
    void buildCompetitorBlock_wrapsEvidenceAndSkipsNullEntries() {
        SeoEvidence e1 =
                new SeoEvidence(
                        "https://a.com?q=1&x=<y>",
                        "Title <tag>",
                        "Snippet & \"quote\"",
                        1.0,
                        Optional.of(Instant.parse("2024-01-15T00:00:00Z")),
                        "CAT");
        ArrayList<SeoEvidence> withNull = new ArrayList<>();
        withNull.add(null);
        withNull.add(e1);
        String xml = SeoEvidenceXmlBuilder.buildCompetitorBlock(withNull);
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
