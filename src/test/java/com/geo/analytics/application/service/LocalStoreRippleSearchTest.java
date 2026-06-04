package com.geo.analytics.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.geo.analytics.application.dto.ExtractedPlace;
import com.geo.analytics.application.dto.TargetAttributes;
import com.geo.analytics.domain.enums.IndustryType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LocalStoreRippleSearchTest {

    private static TargetAttributes attrs(String categoryKeyword, String ward, String city) {
        return new TargetAttributes(IndustryType.YMYL, categoryKeyword, null, city, ward, null, "note");
    }

    @Test
    void usesCategoryKeyword_notCoarseIndustryLabel() {
        PlacesSearchService places = mock(PlacesSearchService.class);
        when(places.search(any(), any())).thenReturn(List.of());
        LocalStoreRippleSearch sut = new LocalStoreRippleSearch(places);
        UUID projectId = UUID.randomUUID();

        sut.collectMergedPlaces(projectId, attrs("訪問看護", "港北区", "横浜市"), IndustryType.YMYL);

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(places, org.mockito.Mockito.atLeastOnce()).search(eq(projectId), queryCaptor.capture());
        // 職種ワードが検索語に使われ、粗い業種ラベル「YMYL分野」は使われない
        assertThat(queryCaptor.getAllValues()).anyMatch(q -> q.contains("港北区") && q.contains("訪問看護"));
        assertThat(queryCaptor.getAllValues()).noneMatch(q -> q.contains("YMYL分野"));
    }

    @Test
    void fallsBackToIndustryLabel_whenCategoryKeywordBlank() {
        PlacesSearchService places = mock(PlacesSearchService.class);
        when(places.search(any(), any())).thenReturn(List.of());
        LocalStoreRippleSearch sut = new LocalStoreRippleSearch(places);
        UUID projectId = UUID.randomUUID();

        sut.collectMergedPlaces(projectId, attrs(null, "港北区", "横浜市"), IndustryType.YMYL);

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(places, org.mockito.Mockito.atLeastOnce()).search(eq(projectId), queryCaptor.capture());
        // 職種ワード未取得時は従来の業種ラベルへフォールバック
        assertThat(queryCaptor.getAllValues()).anyMatch(q -> q.contains("YMYL分野"));
    }
}
