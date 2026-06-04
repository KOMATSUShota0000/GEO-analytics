package com.geo.analytics.application.service;

import com.geo.analytics.application.dto.ExtractedPlace;
import com.geo.analytics.application.dto.TargetAttributes;
import com.geo.analytics.domain.enums.IndustryType;
import com.geo.analytics.domain.exception.InsufficientCreditException;
import com.geo.analytics.domain.service.EntityNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Component
public class LocalStoreRippleSearch {

    private static final Logger log = LoggerFactory.getLogger(LocalStoreRippleSearch.class);

    private static final int MAX_ROUND_QUERIES = 3;
    private static final int EARLY_EXIT_NONBLANK_URL_COUNT = 10;
    private static final int MAX_ACCUMULATOR_SIZE = 30;

    private final PlacesSearchService placesSearchService;

    public LocalStoreRippleSearch(PlacesSearchService placesSearchService) {
        this.placesSearchService = placesSearchService;
    }

    public List<ExtractedPlace> collectMergedPlaces(
            UUID projectId, TargetAttributes targetAttributes, IndustryType industry) {
        List<String> queries = buildDistinctQueries(targetAttributes, industry);
        List<ExtractedPlace> accumulator = new ArrayList<>();
        Set<String> seenKeys = new LinkedHashSet<>();
        for (int queryRoundIndex = 0; queryRoundIndex < queries.size(); queryRoundIndex++) {
            String query = queries.get(queryRoundIndex);
            try {
                List<ExtractedPlace> batch = placesSearchService.search(projectId, query);
                mergeBatch(accumulator, seenKeys, batch);
            } catch (InsufficientCreditException insufficientCreditException) {
                log.warn(
                        "ripple search stopped insufficient credit projectId={} queryRoundIndex={} query={}",
                        projectId,
                        queryRoundIndex,
                        query);
                break;
            } catch (Throwable throwable) {
                log.warn(
                        "ripple search round failed projectId={} queryRoundIndex={} query={} errorType={} message={}",
                        projectId,
                        queryRoundIndex,
                        query,
                        throwable.getClass().getName(),
                        throwable.getMessage() != null ? throwable.getMessage() : "");
                continue;
            }
            if (countNonBlankWebsiteUrl(accumulator) >= EARLY_EXIT_NONBLANK_URL_COUNT) {
                log.info(
                        "ripple search early exit sufficient nonBlankUrls projectId={} nonBlankUrlCount={} threshold={}",
                        projectId,
                        countNonBlankWebsiteUrl(accumulator),
                        EARLY_EXIT_NONBLANK_URL_COUNT);
                break;
            }
            if (accumulator.size() >= MAX_ACCUMULATOR_SIZE) {
                break;
            }
        }
        return List.copyOf(accumulator);
    }

    private static List<String> buildDistinctQueries(TargetAttributes targetAttributes, IndustryType industry) {
        IndustryType safe = industry != null ? industry : IndustryType.OTHER;
        // 具体的職種ワード（例: 訪問看護）を最優先。無ければ粗い業種ラベル（例: YMYL分野）へフォールバック。
        String label = safe.getLabel();
        if (targetAttributes != null
                && targetAttributes.categoryKeyword() != null
                && !targetAttributes.categoryKeyword().isBlank()) {
            label = targetAttributes.categoryKeyword().trim();
        }
        String town = "";
        String ward = "";
        String city = "";
        if (targetAttributes != null) {
            if (targetAttributes.town() != null && !targetAttributes.town().isBlank()) {
                town = targetAttributes.town().trim();
            }
            if (targetAttributes.ward() != null && !targetAttributes.ward().isBlank()) {
                ward = targetAttributes.ward().trim();
            }
            if (targetAttributes.city() != null && !targetAttributes.city().isBlank()) {
                city = targetAttributes.city().trim();
            }
        }
        List<String> raw = new ArrayList<>();
        if (!town.isEmpty()) {
            raw.add(town + " " + label);
        }
        if (!ward.isEmpty()) {
            raw.add(ward + " " + label);
        }
        if (!city.isEmpty()) {
            raw.add(city + " " + label);
        }
        LinkedHashSet<String> normalizedSeen = new LinkedHashSet<>();
        List<String> out = new ArrayList<>();
        for (String q : raw) {
            String trimmed = q != null ? q.trim() : "";
            if (trimmed.isEmpty()) {
                continue;
            }
            String norm = normalizeQueryKey(trimmed);
            if (norm.isEmpty()) {
                continue;
            }
            if (!normalizedSeen.add(norm)) {
                continue;
            }
            out.add(trimmed);
            if (out.size() >= MAX_ROUND_QUERIES) {
                break;
            }
        }
        return out;
    }

    private static String normalizeQueryKey(String q) {
        return q.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static void mergeBatch(
            List<ExtractedPlace> accumulator,
            Set<String> seenKeys,
            List<ExtractedPlace> batch) {
        if (batch == null || batch.isEmpty()) {
            return;
        }
        for (ExtractedPlace place : batch) {
            if (accumulator.size() >= MAX_ACCUMULATOR_SIZE) {
                break;
            }
            String key = dedupeKey(place);
            if (key.isEmpty()) {
                continue;
            }
            if (!seenKeys.add(key)) {
                continue;
            }
            accumulator.add(place);
        }
    }

    private static String dedupeKey(ExtractedPlace place) {
        if (place == null) {
            return "";
        }
        String url = place.websiteUrl();
        if (url != null && !url.isBlank()) {
            String host = EntityNormalizer.hostLabelFromUrl(url);
            if (host != null && !host.isBlank()) {
                return "h:" + host.toLowerCase(Locale.ROOT);
            }
        }
        String name = place.name();
        if (name != null && !name.isBlank()) {
            return "n:" + name.strip().toLowerCase(Locale.ROOT);
        }
        return "";
    }

    private static int countNonBlankWebsiteUrl(List<ExtractedPlace> list) {
        int n = 0;
        for (ExtractedPlace p : list) {
            if (p == null) {
                continue;
            }
            String u = p.websiteUrl();
            if (u != null && !u.isBlank()) {
                n++;
            }
        }
        return n;
    }
}
