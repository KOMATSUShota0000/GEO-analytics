package com.geo.analytics.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.geo.analytics.domain.entity.ProjectEntity;
import com.geo.analytics.domain.enums.IndustryType;
import org.junit.jupiter.api.Test;

class JobProjectResponseTest {

    @Test
    void from_appliesFallbacks_whenBrandColorAndIndustryTypeAreAbsent() {
        ProjectEntity project = new ProjectEntity();
        project.setName("Acme");
        project.setTargetUrl("https://acme.example");
        project.setBrandColor("   ");
        project.setIndustryType(null);

        JobProjectResponse response = JobProjectResponse.from(project);

        // ホワイトラベル未設定でもレポートが破綻しないよう既定色へフォールバックする。
        assertThat(response.brandColor()).isEqualTo("#4F46E5");
        assertThat(response.industryType()).isEqualTo(IndustryType.OTHER);
    }

    @Test
    void from_carriesConfiguredValues() {
        ProjectEntity project = new ProjectEntity();
        project.setName("Acme");
        project.setTargetUrl("https://acme.example");
        project.setBrandColor("#123456");
        project.setLogoUrl("https://acme.example/logo.png");
        project.setIndustryType(IndustryType.B2B);

        JobProjectResponse response = JobProjectResponse.from(project);

        assertThat(response.projectName()).isEqualTo("Acme");
        assertThat(response.targetUrl()).isEqualTo("https://acme.example");
        assertThat(response.brandColor()).isEqualTo("#123456");
        assertThat(response.logoUrl()).isEqualTo("https://acme.example/logo.png");
        assertThat(response.industryType()).isEqualTo(IndustryType.B2B);
    }
}
