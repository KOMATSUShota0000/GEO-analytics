package com.geo.analytics.application.service;

import com.geo.analytics.application.dto.BenchmarkCandidate;
import com.geo.analytics.domain.enums.BenchmarkSource;
import com.geo.analytics.domain.enums.IndustryType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class VirtualBenchmarkProvider {

    private static final Map<IndustryType, List<String>> TEMPLATE_NAMES;

    static {
        EnumMap<IndustryType, List<String>> templateNames =
                new EnumMap<>(IndustryType.class);
        templateNames.put(
                IndustryType.YMYL,
                List.of("規模モデル総合クリニックA", "標準構成専門診療所B", "地域連携グループクリニックC"));
        templateNames.put(
                IndustryType.LOCAL,
                List.of("同立地店舗型サービス提供者P", "同商圏小規模事業者Q", "近隣独立店ブランドR"));
        templateNames.put(
                IndustryType.B2B,
                List.of("想定競合サービス会社X", "同セグメントSaaS並走企業Y", "専門度合い近いコンサルZ"));
        templateNames.put(
                IndustryType.B2C,
                List.of("身近コンシューマサービスα", "同価値帯体験商材β", "ローカル顧客体験運用γ"));
        templateNames.put(
                IndustryType.EC,
                List.of("直接販売型ショップD", "同カテゴリ独立店E", "ニッチ特化通販F"));
        templateNames.put(
                IndustryType.OTHER,
                List.of("汎用標準事業者M1", "同規模想定プレーヤーM2", "周辺代替サービスM3"));
        TEMPLATE_NAMES = Collections.unmodifiableMap(templateNames);
    }

    public List<BenchmarkCandidate> generateDefaults(IndustryType industry, String location, int requiredCount) {
        if (requiredCount <= 0) {
            return List.of();
        }
        IndustryType key = industry == null ? IndustryType.OTHER : industry;
        List<String> pool = TEMPLATE_NAMES.getOrDefault(key, TEMPLATE_NAMES.get(IndustryType.OTHER));
        String loc = location == null ? "" : location.trim();
        String geo = loc.isEmpty() ? "" : loc + "における";
        String reasonBase =
                "実在ライバルが不足のため、" + geo + key.getLabel() + "の業種一般の基準を仮想ベンチマークとして提示";
        ArrayList<BenchmarkCandidate> out = new ArrayList<>(requiredCount);
        for (int i = 0; i < requiredCount; i++) {
            String name = pool.get(i % pool.size());
            out.add(new BenchmarkCandidate(name, "", null, null, BenchmarkSource.VIRTUAL_FALLBACK, reasonBase));
        }
        return Collections.unmodifiableList(out);
    }
}
