package com.geo.analytics.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.geo.analytics.domain.entity.JobEntity;
import com.geo.analytics.domain.model.MinorityReport;
import java.util.List;
import org.junit.jupiter.api.Test;

/** #82: オンボーディング議論の少数意見を、毎回の解析プロンプトの前置きに載せる。 */
class JobPromptContextFormatterTest {

    private static JobEntity job() {
        JobEntity job = new JobEntity();
        job.setBusinessSummary("会計ソフトのSaaS");
        job.setTargetAudience("中小企業の経理");
        return job;
    }

    @Test
    void 少数意見は見出し付きで前置きに載る() {
        String context = JobPromptContextFormatter.format(
                job(), List.of(new MinorityReport("業界統計を自社で作る", "工数が読めない", "強み: 独自データ")));

        assertThat(context).contains("【少数意見の見立て");
        assertThat(context).contains("業界統計を自社で作る");
        assertThat(context).contains("見送った理由: 工数が読めない");
        // evidence は解析側の判断材料にならないため載せない
        assertThat(context).doesNotContain("強み: 独自データ");
    }

    @Test
    void 少数意見が無ければ見出しごと出さない() {
        assertThat(JobPromptContextFormatter.format(job(), List.of())).doesNotContain("【少数意見の見立て");
        assertThat(JobPromptContextFormatter.format(job())).doesNotContain("【少数意見の見立て");
        assertThat(JobPromptContextFormatter.format(job(), List.of(new MinorityReport("  ", "理由", "根拠"))))
                .doesNotContain("【少数意見の見立て");
    }

    @Test
    void 件数と文字数を刈ってプロンプトの膨張を防ぐ() {
        String longInsight = "あ".repeat(400);
        var reports = List.of(
                new MinorityReport(longInsight, "理由1", ""),
                new MinorityReport("2件目", "理由2", ""),
                new MinorityReport("3件目", "理由3", ""),
                new MinorityReport("4件目は上限超過で落ちる", "理由4", ""));

        String context = JobPromptContextFormatter.format(job(), reports);

        assertThat(context).doesNotContain("4件目は上限超過で落ちる");
        assertThat(context).contains("3件目");
        assertThat(context).doesNotContain("あ".repeat(151));
        assertThat(context).contains("あ".repeat(150));
    }

    @Test
    void ジョブがnullでも落ちない() {
        assertThat(JobPromptContextFormatter.format(null, List.of(new MinorityReport("案", "理由", ""))))
                .isNotNull();
    }
}
