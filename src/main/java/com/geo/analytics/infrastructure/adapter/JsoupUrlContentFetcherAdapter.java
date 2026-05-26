package com.geo.analytics.infrastructure.adapter;

import com.geo.analytics.domain.exception.ScrapingException;
import com.geo.analytics.domain.port.UrlContentFetcher;
import com.geo.analytics.infrastructure.security.SsrfValidator;
import com.geo.analytics.infrastructure.util.HtmlSanitizer;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Objects;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * {@link UrlContentFetcher} の Jsoup 実装。SSRF 検証・通信制限・HTML 浄化を統合する。
 */
@Component
public class JsoupUrlContentFetcherAdapter implements UrlContentFetcher {

    private static final Logger log = LoggerFactory.getLogger(JsoupUrlContentFetcherAdapter.class);

    private static final int TIMEOUT_MS = 5_000;

    private static final int MAX_BODY_BYTES = 1_048_576;

    private static final int MAX_REDIRECTS = 5;

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private final SsrfValidator ssrfValidator;

    private final HtmlSanitizer htmlSanitizer;

    public JsoupUrlContentFetcherAdapter(SsrfValidator ssrfValidator, HtmlSanitizer htmlSanitizer) {
        this.ssrfValidator = Objects.requireNonNull(ssrfValidator, "ssrfValidator");
        this.htmlSanitizer = Objects.requireNonNull(htmlSanitizer, "htmlSanitizer");
    }

    @Override
    public String fetchCleanText(String url) {
        String currentUrl = url;
        int redirectsFollowed = 0;
        try {
            while (true) {
                ssrfValidator.validate(currentUrl);

                Connection.Response response = Jsoup.connect(currentUrl)
                        .userAgent(USER_AGENT)
                        .timeout(TIMEOUT_MS)
                        .maxBodySize(MAX_BODY_BYTES)
                        .followRedirects(false)
                        .ignoreHttpErrors(true)
                        .execute();

                int code = response.statusCode();
                if (code == 200) {
                    Document doc = response.parse();
                    return htmlSanitizer.sanitize(doc);
                }
                if (isRedirect(code)) {
                    if (redirectsFollowed >= MAX_REDIRECTS) {
                        throw new ScrapingException("Too many redirects (max " + MAX_REDIRECTS + ")");
                    }
                    String rawLocation = response.header("Location");
                    if (rawLocation == null || rawLocation.isBlank()) {
                        throw new ScrapingException("Redirect Location header is missing or empty");
                    }
                    URL base = response.url();
                    currentUrl = resolveRedirectTarget(base, rawLocation.strip());
                    redirectsFollowed++;
                    continue;
                }
                throw new ScrapingException(
                        "Unexpected HTTP status: " + code + " for URL " + currentUrl);
            }
        } catch (ScrapingException e) {
            throw e;
        } catch (IOException e) {
            log.warn(
                    "Fetch failed hostHint={} message={}",
                    safeHostHint(url),
                    e.getMessage());
            throw new ScrapingException("Failed to fetch URL: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            log.warn("Unexpected error while fetching or sanitizing hostHint={}", safeHostHint(url), e);
            throw new ScrapingException("Failed to process URL: " + e.getMessage(), e);
        }
    }

    private static boolean isRedirect(int code) {
        return code == 301
                || code == 302
                || code == 303
                || code == 307
                || code == 308;
    }

    /**
     * {@code Location} を {@code response.url()} を基底とする絶対 URI へ解決する。
     */
    private static String resolveRedirectTarget(URL responseBaseUrl, String location) {
        try {
            URI base = responseBaseUrl.toURI();
            URI resolved = base.resolve(new URI(location));
            if (!resolved.isAbsolute()) {
                throw new ScrapingException("Failed to resolve redirect to an absolute URL");
            }
            return resolved.toASCIIString();
        } catch (URISyntaxException e) {
            throw new ScrapingException("Invalid redirect Location: " + location, e);
        }
    }

    private static String safeHostHint(String url) {
        if (url == null) {
            return "(null)";
        }
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            return host != null ? host : truncate(url, 64);
        } catch (URISyntaxException e) {
            return truncate(url, 64);
        }
    }

    private static String truncate(String s, int maxChars) {
        if (s.length() <= maxChars) {
            return s;
        }
        return s.substring(0, maxChars) + "...";
    }
}
