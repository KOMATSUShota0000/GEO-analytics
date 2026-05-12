package com.geo.analytics.application.service;

import com.geo.analytics.application.dto.ExtractedPlace;
import com.geo.analytics.domain.enums.IndustryType;
import com.geo.analytics.domain.exception.InsufficientCreditException;
import com.geo.analytics.domain.service.EntityNormalizer;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Component
public class LocalStoreRippleSearch {

    private static final int MAX_ROUND_QUERIES = 3;
    private static final int EARLY_EXIT_NONBLANK_URL_COUNT = 10;
    private static final int MAX_ACCUMULATOR_SIZE = 30;

    private final PlacesSearchService placesSearchService;

    public LocalStoreRippleSearch(PlacesSearchService placesSearchService) {
        this.placesSearchService = placesSearchService;
    }

    public List<ExtractedPlace> collectMergedPlaces(UUID projectId, String tradeAreaLabel, IndustryType industry) {
        String area = tradeAreaLabel != null ? tradeAreaLabel.trim() : "";
        List<String> queries = buildDistinctQueries(area, industry);
        List<ExtractedPlace> accumulator = new ArrayList<>();
        Set<String> seenKeys = new LinkedHashSet<>();
        for (String query : queries) {
            try {
                List<ExtractedPlace> batch = placesSearchService.search(projectId, query);
                mergeBatch(accumulator, seenKeys, batch);
            } catch (InsufficientCreditException insufficientCreditException) {
                break;
            } catch (Throwable throwable) {
                continue;
            }
            if (countNonBlankWebsiteUrl(accumulator) >= EARLY_EXIT_NONBLANK_URL_COUNT) {
                break;
            }
            if (accumulator.size() >= MAX_ACCUMULATOR_SIZE) {
                break;
            }
        }
        return List.copyOf(accumulator);
    }

    private static List<String> buildDistinctQueries(String area, IndustryType industry) {
        String label = industry.getLabel();
        String search = industry.getSearchLabel();
        String industrySecond = search != null && !search.isBlank() ? search.trim() : label;
        List<String> raw = new ArrayList<>();
        raw.add(area + " " + label);
        raw.add(industrySecond + " " + area);
        raw.add(label + " 日本");
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
