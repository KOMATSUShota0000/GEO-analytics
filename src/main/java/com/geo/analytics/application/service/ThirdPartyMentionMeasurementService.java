package com.geo.analytics.application.service;

import com.geo.analytics.domain.service.ThirdPartyMentionScorer;
import com.geo.analytics.infrastructure.api.dto.SerpOrganicResult;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * V13_GEO4AXIS の④権威・エンティティ認知（中核＝第三者言及）を実測するサービス。
 *
 * <p>既存の SerpAPI 基盤（{@link GeoCompetitorSearchService}）を流用し、ブランドが言及されている
 * ページ群を取得して、自社ドメイン以外の独立第三者ドメイン数を {@link ThirdPartyMentionScorer} で
 * 0-20 点へ換算する。「外部メディア参照 = 生成AIからの信頼」という GEO の因果を点数化する。
 */
@Service
public class ThirdPartyMentionMeasurementService {

    private static final Logger log = LoggerFactory.getLogger(ThirdPartyMentionMeasurementService.class);

    private final GeoCompetitorSearchService geoCompetitorSearchService;

    public ThirdPartyMentionMeasurementService(GeoCompetitorSearchService geoCompetitorSearchService) {
        this.geoCompetitorSearchService = geoCompetitorSearchService;
    }

    /**
     * 第三者言及を1解析あたり SerpAPI 1コールで実測し、権威中核スコア(0-20)を返す。
     *
     * @param projectId       クレジット予約のテナント解決に用いる
     * @param brandName       測定対象ブランド名
     * @param categoryKeyword 同名誤判定を避けるための職種ワード（ADR-021 由来。空可）
     * @param selfUrl         自社URL。同一ホスト・サブドメインは第三者から除外する
     */
    public AuthorityMentionResult measure(
            UUID projectId, String brandName, String categoryKeyword, String selfUrl) {
        if (projectId == null || brandName == null || brandName.isBlank()) {
            return AuthorityMentionResult.empty();
        }
        String query = buildDisambiguatedQuery(brandName, categoryKeyword);
        // コスト上限: 第三者言及の測定は SerpAPI 1コールに限定（核④・限界利益率）。
        // クレジット予約/確定/返金は GeoCompetitorSearchService 側で担保される。
        List<SerpOrganicResult> results = geoCompetitorSearchService.searchOrganic(projectId, query);
        List<String> urls = results.stream()
                .map(SerpOrganicResult::link)
                .filter(link -> link != null && !link.isBlank())
                .toList();
        int distinct = ThirdPartyMentionScorer.distinctThirdPartyDomainCount(urls, selfUrl);
        double score = ThirdPartyMentionScorer.scoreFromMentionUrls(urls, selfUrl);
        log.info(
                "third_party_mention measured brand=\"{}\" query=\"{}\" hits={} distinctThirdParty={} authorityCore={}",
                brandName,
                query,
                urls.size(),
                distinct,
                score);
        return new AuthorityMentionResult(score, distinct);
    }

    private static String buildDisambiguatedQuery(String brandName, String categoryKeyword) {
        String brand = brandName.strip();
        if (categoryKeyword != null && !categoryKeyword.isBlank()) {
            return brand + " " + categoryKeyword.strip();
        }
        return brand;
    }

    /** 権威中核の実測結果。distinctThirdPartyDomainCount はアドバイス文（改善ロードマップ）の根拠に用いる。 */
    public record AuthorityMentionResult(double authorityCoreScore, int distinctThirdPartyDomainCount) {
        public static AuthorityMentionResult empty() {
            return new AuthorityMentionResult(0.0d, 0);
        }
    }
}
