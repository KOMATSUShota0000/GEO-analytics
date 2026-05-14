package com.geo.analytics.application.service;

import com.geo.analytics.application.dto.CrawledPageData;
import com.geo.analytics.application.dto.DiscoveredLink;
import com.geo.analytics.application.dto.DomainDeepAuditContext;
import com.geo.analytics.application.dto.SmartCrawlPage;
import com.geo.analytics.application.port.LinkHarvestingPort;
import com.geo.analytics.application.port.WebCrawlerPort;
import com.geo.analytics.infrastructure.crawler.CorePageLinkScorer;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SmartDomainCrawlService {

    private static final Logger log = LoggerFactory.getLogger(SmartDomainCrawlService.class);
    private static final int STACK_TRACE_LIMIT = 20_000;

    private final WebCrawlerPort webCrawlerPort;
    private final LinkHarvestingPort linkHarvestingPort;

    public SmartDomainCrawlService(WebCrawlerPort webCrawlerPort, LinkHarvestingPort linkHarvestingPort) {
        this.webCrawlerPort = webCrawlerPort;
        this.linkHarvestingPort = linkHarvestingPort;
    }

    public DomainDeepAuditContext compileForAudit(String entryUrl) {
        if (entryUrl == null || entryUrl.isBlank()) {
            throw new IllegalArgumentException("entryUrl");
        }
        String canonicalEntryUrl = entryUrl.trim();
        CrawledPageData top = webCrawlerPort.extractContent(canonicalEntryUrl);
        List<DiscoveredLink> discovered = List.of();
        try {
            discovered = linkHarvestingPort.harvestHtmlAnchorsSameOrigin(canonicalEntryUrl);
            if (discovered == null) {
                discovered = List.of();
            }
        } catch (RuntimeException runtimeException) {
            log.warn(
                    "smart_domain_crawl_link_harvest_failed entryUrl={} trace={}",
                    canonicalEntryUrl,
                    truncateStackTrace(runtimeException));
            discovered = List.of();
        }
        List<String> followUrls = CorePageLinkScorer.selectTopFollowUrls(discovered, canonicalEntryUrl);
        ArrayList<SmartCrawlPage> pages = new ArrayList<>(3);
        pages.add(new SmartCrawlPage(canonicalEntryUrl, top, 0));
        int ordinal = 1;
        for (int i = 0; i < followUrls.size() && pages.size() < 3; i++) {
            String follow = followUrls.get(i);
            if (follow == null || follow.isBlank()) {
                continue;
            }
            try {
                CrawledPageData sub = webCrawlerPort.extractContent(follow.trim());
                pages.add(new SmartCrawlPage(follow.trim(), sub, ordinal));
                ordinal++;
            } catch (RuntimeException runtimeException) {
                log.warn(
                        "smart_domain_crawl_follow_failed entryUrl={} followUrl={} trace={}",
                        canonicalEntryUrl,
                        follow,
                        truncateStackTrace(runtimeException));
            }
        }
        String merged = buildMergedAuditText(pages);
        int cap = DomainDeepAuditContext.MERGED_TOTAL_MAX_CHARS;
        merged = clipToCodePointLimit(merged, cap);
        return new DomainDeepAuditContext(List.copyOf(pages), merged, canonicalEntryUrl);
    }

    private static String buildMergedAuditText(List<SmartCrawlPage> pages) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < pages.size(); i++) {
            SmartCrawlPage sp = pages.get(i);
            if (sp == null) {
                continue;
            }
            String u = sp.url() == null ? "" : sp.url();
            builder.append("\n---\nURL: ");
            builder.append(u);
            builder.append("\n---\n");
            if (sp.crawled() != null) {
                String ev = sp.crawled().seoTechnicalEvidenceSummary();
                if (ev != null && !ev.isBlank()) {
                    builder.append("【技術的エビデンス（SEO / クローラビリティ）】\n");
                    builder.append(ev.strip());
                    builder.append("\n---\n");
                }
            }
            String body = "";
            if (sp.crawled() != null && sp.crawled().mainContent() != null) {
                body = sp.crawled().mainContent();
            }
            builder.append(body);
        }
        return builder.toString();
    }

    private static String clipToCodePointLimit(String text, int maxCodePoints) {
        if (text == null || text.isEmpty() || maxCodePoints <= 0) {
            return text == null ? "" : text;
        }
        return text.codePoints()
                .limit(maxCodePoints)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
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
