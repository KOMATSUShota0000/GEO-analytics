package com.geo.analytics.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.geo.analytics.domain.entity.ProjectEntity;
import com.geo.analytics.domain.model.CompetitorProfile;
import java.util.List;
import org.junit.jupiter.api.Test;

class JobProjectResponseTest {

    @Test
    void from_carriesCompetitorProfiles_realAndSynthetic() {
        ProjectEntity project = new ProjectEntity();
        project.setName("Acme");
        project.setTargetUrl("https://acme.example");
        project.setCompetitorUrls(List.of("https://rival.example", "", ""));
        project.setCompetitorProfiles(List.of(
                new CompetitorProfile("rival.example", "https://rival.example", false),
                new CompetitorProfile("東京都におけるその他の中央値参照モデルB", null, true)));

        JobProjectResponse response = JobProjectResponse.from(project);

        assertThat(response.competitorProfiles()).hasSize(2);
        CompetitorProfile real = response.competitorProfiles().get(0);
        assertThat(real.synthetic()).isFalse();
        assertThat(real.websiteUrl()).isEqualTo("https://rival.example");
        CompetitorProfile synthetic = response.competitorProfiles().get(1);
        assertThat(synthetic.synthetic()).isTrue();
        // 合成（参考基準点）競合は実在しないため URL を持たない。
        assertThat(synthetic.websiteUrl()).isNull();
        // 既存の competitorUrls は非破壊で維持される。
        assertThat(response.competitorUrls()).containsExactly("https://rival.example", "", "");
    }
}
