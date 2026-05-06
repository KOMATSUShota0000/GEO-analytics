package com.geo.analytics.application.service;

import com.geo.analytics.application.dto.BenchmarkCandidate;
import com.geo.analytics.application.dto.CompetitorInferenceResult;
import com.geo.analytics.application.dto.CrawledPageData;
import com.geo.analytics.application.dto.ExtractedPlace;
import com.geo.analytics.application.dto.OneClickCompetitorExtractionResult;
import com.geo.analytics.application.dto.PageSignalsForInference;
import com.geo.analytics.application.port.WebCrawlerPort;
import com.geo.analytics.domain.enums.BenchmarkSource;
import com.geo.analytics.domain.enums.IndustryType;
import com.geo.analytics.infrastructure.ai.GeminiCompetitorInferenceAdapter;
import com.geo.analytics.infrastructure.ai.GeminiGiantFilterAdapter;
import com.geo.analytics.infrastructure.api.GooglePlacesAdapter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OneClickCompetitorExtractionService {

    private static final Logger log = LoggerFactory.getLogger(OneClickCompetitorExtractionService.class);
    private static final int REQUIRED_COUNT = 3;
    private static final int STACK_TRACE_LIMIT = 20_000;

    private final WebCrawlerPort webCrawlerPort;
    private final GeminiCompetitorInferenceAdapter competitorInferenceAdapter;
    private final GooglePlacesAdapter googlePlacesAdapter;
    private final GeminiGiantFilterAdapter giantFilterAdapter;
    private final VirtualBenchmarkProvider virtualBenchmarkProvider;
    private final JobPersistenceService jobPersistenceService;

    public OneClickCompetitorExtractionService(
            WebCrawlerPort webCrawlerPort,
            GeminiCompetitorInferenceAdapter competitorInferenceAdapter,
            GooglePlacesAdapter googlePlacesAdapter,
            GeminiGiantFilterAdapter giantFilterAdapter,
            VirtualBenchmarkProvider virtualBenchmarkProvider,
            JobPersistenceService jobPersistenceService) {
        this.webCrawlerPort = webCrawlerPort;
        this.competitorInferenceAdapter = competitorInferenceAdapter;
        this.googlePlacesAdapter = googlePlacesAdapter;
        this.giantFilterAdapter = giantFilterAdapter;
        this.virtualBenchmarkProvider = virtualBenchmarkProvider;
        this.jobPersistenceService = jobPersistenceService;
    }

    public OneClickCompetitorExtractionResult extract(UUID projectId, String selfUrl) {
        if (projectId == null) {
            throw new IllegalArgumentException("projectId");
        }
        if (selfUrl == null || selfUrl.isBlank()) {
            throw new IllegalArgumentException("selfUrl");
        }
        String trimmedUrl = selfUrl.trim();
        String pageText = null;
        try {
            CrawledPageData page = webCrawlerPort.extractContent(trimmedUrl);
            if (page != null) {
                pageText = page.mainContent();
            }
        } catch (Exception exception) {
            log.warn(
                    "one_click_competitor_fallback stage=crawl projectId={} trace={}",
                    projectId,
                    truncateStackTrace(exception));
            return buildFullFallback(projectId, IndustryType.OTHER, "", "");
        }
        if (pageText == null || pageText.isBlank()) {
            log.warn("one_click_competitor_fallback stage=crawl_empty_content projectId={}", projectId);
            return buildFullFallback(projectId, IndustryType.OTHER, "", "");
        }
        IndustryType industry = IndustryType.OTHER;
        String location = "";
        String evidence = "";
        try {
            CompetitorInferenceResult inf =
                    competitorInferenceAdapter.infer(new PageSignalsForInference(pageText, trimmedUrl));
            if (inf.industry() != null) {
                industry = inf.industry();
            }
            if (inf.location() != null) {
                location = inf.location().trim();
            }
            if (inf.evidence() != null) {
                evidence = inf.evidence().trim();
            }
        } catch (Exception exception) {
            log.warn(
                    "one_click_competitor_fallback stage=inference projectId={} trace={}",
                    projectId,
                    truncateStackTrace(exception));
            return buildFullFallback(projectId, IndustryType.OTHER, "", "");
        }
        List<ExtractedPlace> places = List.of();
        if (!location.isEmpty()) {
            try {
                places = googlePlacesAdapter.searchCompetingBusinesses(projectId, location, industry);
                if (places == null) {
                    places = List.of();
                }
            } catch (Exception exception) {
                log.warn(
                        "one_click_competitor_fallback stage=places projectId={} trace={}",
                        projectId,
                        truncateStackTrace(exception));
                places = List.of();
            }
        }
        List<BenchmarkCandidate> filtered = List.of();
        if (!places.isEmpty()) {
            try {
                filtered =
                        giantFilterAdapter.filterToBenchmarks(
                                projectId, industry, location, trimmedUrl, places);
                if (filtered == null) {
                    filtered = List.of();
                }
            } catch (Exception exception) {
                log.warn(
                        "one_click_competitor_fallback stage=filter projectId={} trace={}",
                        projectId,
                        truncateStackTrace(exception));
                filtered = List.of();
            }
        }
        ArrayList<BenchmarkCandidate> out = new ArrayList<>(REQUIRED_COUNT);
        for (int i = 0; i < filtered.size() && out.size() < REQUIRED_COUNT; i++) {
            BenchmarkCandidate candidate = filtered.get(i);
            if (candidate != null) {
                out.add(candidate);
            }
        }
        int deficit = REQUIRED_COUNT - out.size();
        if (deficit > 0) {
            out.addAll(virtualBenchmarkProvider.generateDefaults(industry, location, deficit));
        }
        boolean usedFallback =
                out.stream().anyMatch(b -> b.source() == BenchmarkSource.VIRTUAL_FALLBACK);
        ArrayList<String> persistUrls = new ArrayList<>(REQUIRED_COUNT);
        for (int i = 0; i < out.size() && persistUrls.size() < REQUIRED_COUNT; i++) {
            BenchmarkCandidate b = out.get(i);
            if (b.source() == BenchmarkSource.LIVE_PLACES
                    && b.websiteUrl() != null
                    && !b.websiteUrl().trim().isEmpty()) {
                persistUrls.add(b.websiteUrl().trim());
            }
        }
        jobPersistenceService.saveProjectCompetitorUrls(projectId, persistUrls);
        return new OneClickCompetitorExtractionResult(
                industry, location, evidence, List.copyOf(out), usedFallback);
    }

    private OneClickCompetitorExtractionResult buildFullFallback(
            UUID projectId, IndustryType industry, String location, String evidence) {
        List<BenchmarkCandidate> benches =
                virtualBenchmarkProvider.generateDefaults(industry, location, REQUIRED_COUNT);
        jobPersistenceService.saveProjectCompetitorUrls(projectId, List.of());
        return new OneClickCompetitorExtractionResult(industry, location, evidence, benches, true);
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
