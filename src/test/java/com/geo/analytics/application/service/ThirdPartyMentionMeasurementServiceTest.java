package com.geo.analytics.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.geo.analytics.infrastructure.api.dto.SerpOrganicResult;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ThirdPartyMentionMeasurementServiceTest {

    private static final String SELF = "https://myco.jp";

    private static SerpOrganicResult result(String link) {
        return new SerpOrganicResult("title", link, "snippet");
    }

    @Test
    void blankBrand_returnsEmpty_andDoesNotCallSearch() {
        GeoCompetitorSearchService search = mock(GeoCompetitorSearchService.class);
        var sut = new ThirdPartyMentionMeasurementService(search);

        var out = sut.measure(UUID.randomUUID(), "  ", "訪問看護", SELF);

        assertThat(out.authorityCoreScore()).isEqualTo(0.0);
        assertThat(out.distinctThirdPartyDomainCount()).isEqualTo(0);
        verify(search, never()).searchOrganic(any(), any());
    }

    @Test
    void nullProject_returnsEmpty() {
        GeoCompetitorSearchService search = mock(GeoCompetitorSearchService.class);
        var sut = new ThirdPartyMentionMeasurementService(search);

        var out = sut.measure(null, "ブランド", "訪問看護", SELF);

        assertThat(out.authorityCoreScore()).isEqualTo(0.0);
        verify(search, never()).searchOrganic(any(), any());
    }

    @Test
    void appendsCategoryKeyword_forDisambiguation() {
        GeoCompetitorSearchService search = mock(GeoCompetitorSearchService.class);
        when(search.searchOrganic(any(), any())).thenReturn(List.of());
        var sut = new ThirdPartyMentionMeasurementService(search);
        UUID projectId = UUID.randomUUID();

        sut.measure(projectId, "ひまわり会", "訪問看護", SELF);

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(search).searchOrganic(eq(projectId), queryCaptor.capture());
        assertThat(queryCaptor.getValue()).contains("ひまわり会").contains("訪問看護");
    }

    @Test
    void excludesSelfDomain_andScoresDistinctThirdParties() {
        GeoCompetitorSearchService search = mock(GeoCompetitorSearchService.class);
        when(search.searchOrganic(any(), any())).thenReturn(List.of(
                result("https://myco.jp/about"),
                result("https://prtimes.jp/x"),
                result("https://note.com/y"),
                result("https://www.prtimes.jp/z")));
        var sut = new ThirdPartyMentionMeasurementService(search);

        var out = sut.measure(UUID.randomUUID(), "ブランド", null, SELF);

        // 自社 myco.jp を除き、独立第三者ドメインは prtimes.jp / note.com の2件 → 20*(2/8)=5.0
        assertThat(out.distinctThirdPartyDomainCount()).isEqualTo(2);
        assertThat(out.authorityCoreScore()).isEqualTo(5.0);
    }
}
