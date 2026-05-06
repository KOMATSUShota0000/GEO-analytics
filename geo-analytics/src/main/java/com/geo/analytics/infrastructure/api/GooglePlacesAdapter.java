package com.geo.analytics.infrastructure.api;

import com.geo.analytics.application.credit.CreditReservation;
import com.geo.analytics.application.dto.ExtractedPlace;
import com.geo.analytics.application.dto.MeoTrust;
import com.geo.analytics.application.port.MEODataPort;
import com.geo.analytics.domain.enums.IndustryType;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
public class GooglePlacesAdapter implements MEODataPort {

    private static final Logger log = LoggerFactory.getLogger(GooglePlacesAdapter.class);
    private static final long PLACES_MEO_CREDIT = 30L;
    private static final long PLACES_COMPETITOR_SEARCH_CREDIT = 30L;
    private static final int STACK_TRACE_LIMIT = 20_000;

    private final PlacesTextSearchClient placesTextSearchClient;
    private GooglePlacesAdapter self;

    public GooglePlacesAdapter(PlacesTextSearchClient placesTextSearchClient) {
        this.placesTextSearchClient = placesTextSearchClient;
    }

    @Autowired
    public void setSelf(@Lazy GooglePlacesAdapter self) {
        this.self = self;
    }

    @Override
    public Map<String, MeoTrust> fetchTrustForArea(UUID projectId, String searchQuery, List<String> targetUrls) {
        if (projectId == null) {
            return Map.of();
        }
        String trimmedQuery = searchQuery == null ? "" : searchQuery.trim();
        if (trimmedQuery.isEmpty() || targetUrls == null || targetUrls.isEmpty()) {
            return Map.of();
        }
        List<ExtractedPlace> places;
        try {
            places = self.searchTextWithReservation(projectId, trimmedQuery);
        } catch (RuntimeException runtimeException) {
            log.error(
                    "places_meo_fetch_failed projectId={} query={} trace={}",
                    projectId,
                    trimmedQuery,
                    truncateStackTrace(runtimeException));
            return emptyForAll(targetUrls);
        }
        if (places == null || places.isEmpty()) {
            return emptyForAll(targetUrls);
        }
        LinkedHashMap<String, MeoTrust> out = new LinkedHashMap<>(targetUrls.size());
        for (int i = 0; i < targetUrls.size(); i++) {
            String url = targetUrls.get(i);
            if (url == null) {
                continue;
            }
            String trimmedUrl = url.trim();
            if (trimmedUrl.isEmpty()) {
                continue;
            }
            ExtractedPlace matched = matchByHost(places, trimmedUrl);
            if (matched == null) {
                out.put(trimmedUrl, new MeoTrust(0.0d, 0));
            } else {
                double stars = matched.rating() != null ? matched.rating() : 0.0d;
                int reviews = matched.userRatingsTotal() != null ? matched.userRatingsTotal() : 0;
                out.put(trimmedUrl, new MeoTrust(stars, reviews));
            }
        }
        return Map.copyOf(out);
    }

    @CreditReservation(amount = PLACES_MEO_CREDIT, settleNote = "places_meo_search")
    public List<ExtractedPlace> searchTextWithReservation(UUID projectId, String searchQuery) {
        if (projectId == null) {
            throw new IllegalArgumentException("projectId");
        }
        return placesTextSearchClient.searchText(searchQuery);
    }

    public List<ExtractedPlace> searchCompetingBusinesses(UUID projectId, String location, IndustryType industry) {
        if (projectId == null) {
            throw new IllegalArgumentException("projectId");
        }
        if (industry == null) {
            throw new IllegalArgumentException("industry");
        }
        String trimmedLocation = location == null ? "" : location.trim();
        if (trimmedLocation.isEmpty()) {
            throw new IllegalArgumentException("location");
        }
        String industryLabel = industry.getSearchLabel() == null ? "" : industry.getSearchLabel().trim();
        String textQuery = industryLabel.isEmpty()
                ? trimmedLocation
                : trimmedLocation + " " + industryLabel;
        return self.searchCompetingBusinessesWithReservation(projectId, textQuery);
    }

    @CreditReservation(amount = PLACES_COMPETITOR_SEARCH_CREDIT, settleNote = "places_competitor_search")
    public List<ExtractedPlace> searchCompetingBusinessesWithReservation(UUID projectId, String textQuery) {
        if (projectId == null) {
            throw new IllegalArgumentException("projectId");
        }
        return placesTextSearchClient.searchText(textQuery);
    }

    private static Map<String, MeoTrust> emptyForAll(List<String> targetUrls) {
        LinkedHashMap<String, MeoTrust> out = new LinkedHashMap<>(targetUrls.size());
        for (int i = 0; i < targetUrls.size(); i++) {
            String url = targetUrls.get(i);
            if (url == null) {
                continue;
            }
            String trimmed = url.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            out.put(trimmed, new MeoTrust(0.0d, 0));
        }
        return Map.copyOf(out);
    }

    private static ExtractedPlace matchByHost(List<ExtractedPlace> places, String targetUrl) {
        String targetHost = canonicalHost(targetUrl);
        if (targetHost == null) {
            return null;
        }
        for (int i = 0; i < places.size(); i++) {
            ExtractedPlace candidate = places.get(i);
            if (candidate == null || candidate.websiteUrl() == null) {
                continue;
            }
            String candidateHost = canonicalHost(candidate.websiteUrl());
            if (candidateHost == null) {
                continue;
            }
            if (candidateHost.equals(targetHost)) {
                return candidate;
            }
        }
        return null;
    }

    private static String canonicalHost(String url) {
        try {
            URI parsed = URI.create(url.trim());
            String host = parsed.getHost();
            if (host == null) {
                return null;
            }
            String lower = host.toLowerCase(Locale.ROOT);
            if (lower.startsWith("www.")) {
                return lower.substring(4);
            }
            return lower;
        } catch (RuntimeException runtimeException) {
            return null;
        }
    }

    private static String truncateStackTrace(Throwable throwable) {
        if (throwable == null) {
            return "";
        }
        StringWriter stringWriter = new StringWriter();
        throwable.printStackTrace(new PrintWriter(stringWriter));
        String full = stringWriter.toString();
        if (full.length() <= STACK_TRACE_LIMIT) {
            return full;
        }
        return full.substring(0, STACK_TRACE_LIMIT);
    }
}
