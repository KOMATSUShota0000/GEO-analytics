package com.geo.analytics.integration.schema;

import static org.assertj.core.api.Assertions.assertThat;

import com.geo.analytics.GeoAnalyticsApplication;
import com.geo.analytics.domain.enums.AiRecognitionState;
import com.geo.analytics.domain.enums.AnalysisPriority;
import com.geo.analytics.domain.enums.BusinessModelType;
import com.geo.analytics.domain.enums.IndustryType;
import com.geo.analytics.domain.enums.JobStatus;
import com.geo.analytics.domain.enums.OrganizationUserRole;
import com.geo.analytics.domain.enums.PreferredEngine;
import com.geo.analytics.domain.enums.RagDomainRuleKind;
import com.geo.analytics.domain.enums.SubscriptionPlan;
import com.geo.analytics.domain.enums.TransactionType;
import com.geo.analytics.integration.PostgresTestBase;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Why: enum 値は Java（{@code @Enumerated}）と DB（V134 の CHECK 制約）で二重に制約される。
 * 二重制約は片方だけ更新されると腐り、本番で初めて constraint violation として露見する。
 * enum への値追加とマイグレーション追加が必ず対になることを、CI で機械的に強制する門番。
 */
@SpringBootTest(classes = GeoAnalyticsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("rls-it")
class EnumCheckConstraintSyncTest extends PostgresTestBase {

    private record EnumColumn(Class<? extends Enum<?>> enumType, String table, String column) {
        String constraintName() {
            return "chk_" + table + "_" + column;
        }

        @Override
        public String toString() {
            return table + "." + column + " <-> " + enumType.getSimpleName();
        }
    }

    // Why: pg_get_constraintdef() は値を 'VALUE'::character varying / 'VALUE'::text の形で出力する。
    //      クォート内のみを拾えば IN リストの値集合が過不足なく取れる。
    private static final Pattern QUOTED_VALUE = Pattern.compile("'([A-Za-z0-9_]+)'::");

    private static final String CONSTRAINT_DEF_SQL =
            "SELECT pg_get_constraintdef(c.oid) FROM pg_constraint c"
                    + " JOIN pg_class t ON t.oid = c.conrelid"
                    + " JOIN pg_namespace n ON n.oid = t.relnamespace"
                    + " WHERE n.nspname = 'public' AND t.relname = ? AND c.conname = ? AND c.contype = 'c'";

    static List<EnumColumn> enumColumns() {
        return List.of(
                new EnumColumn(OrganizationUserRole.class, "organization_users", "role"),
                new EnumColumn(JobStatus.class, "jobs", "job_status"),
                new EnumColumn(BusinessModelType.class, "jobs", "industry_type"),
                new EnumColumn(SubscriptionPlan.class, "jobs", "subscription_plan"),
                new EnumColumn(SubscriptionPlan.class, "workspaces", "subscription_plan"),
                new EnumColumn(IndustryType.class, "projects", "industry_type"),
                new EnumColumn(TransactionType.class, "wallet_transactions", "transaction_type"),
                new EnumColumn(AiRecognitionState.class, "audit_histories", "ai_recognition_state"),
                new EnumColumn(AnalysisPriority.class, "project_keywords", "analysis_priority"),
                new EnumColumn(PreferredEngine.class, "project_keywords", "preferred_engine"),
                new EnumColumn(RagDomainRuleKind.class, "rag_domain_rules", "rule_kind"));
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @ParameterizedTest(name = "{0}")
    @MethodSource("enumColumns")
    void javaEnumとDBのCHECK制約の値集合が一致すること(EnumColumn target) {
        List<String> definitions = jdbcTemplate.queryForList(
                CONSTRAINT_DEF_SQL, String.class, target.table(), target.constraintName());

        assertThat(definitions)
                .as("CHECK制約 %s が存在すること（マイグレーション未追加、または命名規約 chk_<table>_<column> との不一致）",
                        target.constraintName())
                .hasSize(1);

        Set<String> valuesInDb = new LinkedHashSet<>();
        Matcher matcher = QUOTED_VALUE.matcher(definitions.getFirst());
        while (matcher.find()) {
            valuesInDb.add(matcher.group(1));
        }

        Set<String> valuesInJava = Arrays.stream(target.enumType().getEnumConstants())
                .map(Enum::name)
                .collect(Collectors.toUnmodifiableSet());

        assertThat(valuesInDb)
                .as("%s の値集合が Java enum %s と一致すること（enum に値を足したらマイグレーションも追加する）",
                        target.constraintName(), target.enumType().getSimpleName())
                .isEqualTo(valuesInJava);
    }
}
