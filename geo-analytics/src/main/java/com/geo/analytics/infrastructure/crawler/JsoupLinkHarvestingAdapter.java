package com.geo.analytics.infrastructure.crawler;

import com.geo.analytics.application.dto.DiscoveredLink;
import com.geo.analytics.application.port.LinkHarvestingPort;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

@Component
public class JsoupLinkHarvestingAdapter implements LinkHarvestingPort {

    private static final String CHROME_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final int CONNECT_TIMEOUT_MILLIS = 15_000;
    private static final int MAX_ANCHORS = 512;

    @Override
    public List<DiscoveredLink> harvestHtmlAnchorsSameOrigin(String pageUrl) {
        if (pageUrl == null || pageUrl.isBlank()) {
            return List.of();
        }
        String trimmedUrl = pageUrl.trim();
        try {
            Document document = Jsoup.connect(trimmedUrl)
                    .userAgent(CHROME_USER_AGENT)
                    .timeout(CONNECT_TIMEOUT_MILLIS)
                    .ignoreHttpErrors(false)
                    .get();
            String location = document.location();
            String base = location != null && !location.isBlank() ? location : trimmedUrl;
            URI baseUri = URI.create(base);
            String entryHostCanon = canonicalHost(baseUri.getHost());
            if (entryHostCanon.isEmpty()) {
                return List.of();
            }
            Elements anchors = document.select("a[href]");
            int cap = StrictMath.min(MAX_ANCHORS, anchors.size());
            LinkedHashMap<String, DiscoveredLink> byUrlKey = new LinkedHashMap<>();
            for (int i = 0; i < cap; i++) {
                Element anchor = anchors.get(i);
                String hrefRaw = anchor.attr("href");
                if (hrefRaw == null || hrefRaw.isBlank()) {
                    continue;
                }
                String hrefTrim = hrefRaw.strip();
                if (blockedSchemeHref(hrefTrim)) {
                    continue;
                }
                URI resolved = resolveHref(baseUri, hrefTrim);
                if (resolved == null || resolved.getHost() == null || resolved.getScheme() == null) {
                    continue;
                }
                String sch = resolved.getScheme().toLowerCase(Locale.ROOT);
                if (!sch.equals("http") && !sch.equals("https")) {
                    continue;
                }
                if (!canonicalHost(resolved.getHost()).equals(entryHostCanon)) {
                    continue;
                }
                URI normalized = resolved.normalize();
                String rp = normalized.getRawPath();
                String pathNorm = rp == null ? "/" : rp.toLowerCase(Locale.ROOT);
                String anchorText = anchor.text() != null ? anchor.text().strip() : "";
                DiscoveredLink link = new DiscoveredLink(normalized.toString(), pathNorm, anchorText);
                byUrlKey.putIfAbsent(normalized.toString(), link);
            }
            return List.copyOf(new ArrayList<>(byUrlKey.values()));
        } catch (RuntimeException | java.io.IOException ignored) {
            return List.of();
        }
    }

    private static URI resolveHref(URI baseUri, String hrefTrim) {
        try {
            URI hrefUri = URI.create(hrefTrim);
            if (!hrefUri.isAbsolute()) {
                return baseUri.resolve(hrefUri);
            }
            return hrefUri;
        } catch (RuntimeException runtimeException) {
            return null;
        }
    }

    private static boolean blockedSchemeHref(String hrefTrimmed) {
        String lower = hrefTrimmed.toLowerCase(Locale.ROOT);
        return lower.startsWith("mailto:")
                || lower.startsWith("tel:")
                || lower.startsWith("javascript:")
                || lower.startsWith("data:");
    }

    private static String canonicalHost(String host) {
        if (host == null) {
            return "";
        }
        String lower = host.toLowerCase(Locale.ROOT).strip();
        if (lower.startsWith("www.")) {
            return lower.substring(4);
        }
        return lower;
    }
}
