package com.geo.analytics.infrastructure.crawler;

import com.geo.analytics.application.dto.CrawledPageData;
import com.geo.analytics.application.port.WebCrawlerPort;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.Route;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;
import org.apache.commons.codec.digest.DigestUtils;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;

public class PlaywrightWebCrawlerAdapter implements WebCrawlerPort {
    private static final String CHROME_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final int PLAYWRIGHT_DEADLINE_MILLIS = 15_000;
    private static final Set<String> BLOCKED_RESOURCE_TYPES = Set.of("image", "stylesheet", "font", "media");
    private static final List<String> BLOCKED_HOST_SUFFIXES = List.of(
        "google-analytics.com",
        "googletagmanager.com",
        "doubleclick.net",
        "googlesyndication.com",
        "facebook.net",
        "scorecardresearch.com",
        "hotjar.com",
        "clarity.ms",
        "segment.io",
        "segment.com",
        "cdn.optimizely.com",
        "adservice.google",
        "adsystem.com",
        "taboola.com",
        "outbrain.com",
        "criteo.com",
        "amazon-adsystem.com",
        "moatads.com",
        "quantserve.com"
    );
    private final Browser browser;
    private final Semaphore playwrightCrawlSemaphore;

    public PlaywrightWebCrawlerAdapter(Browser browser, Semaphore playwrightCrawlSemaphore) {
        this.browser = browser;
        this.playwrightCrawlSemaphore = playwrightCrawlSemaphore;
    }

    @Override
    public CrawledPageData extractContent(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("url must not be blank");
        }
        String trimmedUrl = url.trim();
        boolean acquired = false;
        try {
            playwrightCrawlSemaphore.acquire();
            acquired = true;
            return crawlWithPlaywright(trimmedUrl);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            return JsoupPageExtractor.extract(trimmedUrl);
        } catch (PlaywrightException playwrightException) {
            return JsoupPageExtractor.extract(trimmedUrl);
        } catch (Exception exception) {
            return JsoupPageExtractor.extract(trimmedUrl);
        } finally {
            if (acquired) {
                playwrightCrawlSemaphore.release();
            }
        }
    }

    private CrawledPageData crawlWithPlaywright(String url) {
        BrowserContext browserContext = browser.newContext(new Browser.NewContextOptions().setUserAgent(CHROME_USER_AGENT));
        try {
            Page page = browserContext.newPage();
            try {
                page.setDefaultNavigationTimeout(PLAYWRIGHT_DEADLINE_MILLIS);
                page.setDefaultTimeout(PLAYWRIGHT_DEADLINE_MILLIS);
                page.route("**/*", PlaywrightWebCrawlerAdapter::handleRoute);
                page.navigate(url, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
                page.waitForLoadState(LoadState.DOMCONTENTLOADED);
                try {
                    page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(5_000));
                } catch (PlaywrightException ignored) {
                }
                Locator bodyLocator = page.locator("body");
                String innerText = bodyLocator.count() > 0 ? bodyLocator.innerText() : "";
                String mainContent = PageContentNormalizer.normalizeVisibleText(innerText);
                List<String> schemaOrg = extractSchemaOrgJsonList(page);
                Map<String, String> metaTags = extractMetaTagsFromPage(page);
                String contentHash = DigestUtils.sha256Hex(mainContent.getBytes(StandardCharsets.UTF_8));
                return new CrawledPageData(url, mainContent, contentHash, schemaOrg, metaTags);
            } finally {
                page.close();
            }
        } finally {
            browserContext.close();
        }
    }

    private static List<String> extractSchemaOrgJsonList(Page page) {
        List<Locator> scriptLocators = page.locator("script[type='application/ld+json']").all();
        List<String> result = new ArrayList<>();
        for (Locator scriptLocator : scriptLocators) {
            String textContent = scriptLocator.textContent();
            if (textContent != null && !textContent.isBlank()) {
                result.add(textContent.strip());
            }
        }
        return List.copyOf(result);
    }

    private static Map<String, String> extractMetaTagsFromPage(Page page) {
        Map<String, String> metaTagMap = new LinkedHashMap<>();
        putMetaProperty(page, metaTagMap, "og:title");
        putMetaProperty(page, metaTagMap, "og:description");
        putMetaProperty(page, metaTagMap, "og:url");
        putMetaProperty(page, metaTagMap, "og:type");
        putMetaName(page, metaTagMap, "description");
        putMetaName(page, metaTagMap, "keywords");
        putMetaName(page, metaTagMap, "robots");
        putMetaName(page, metaTagMap, "viewport");
        return Map.copyOf(metaTagMap);
    }

    private static void putMetaProperty(Page page, Map<String, String> targetMap, String propertyKey) {
        Locator locator = page.locator("meta[property=\"" + propertyKey + "\"]").first();
        if (locator.count() > 0) {
            String contentValue = locator.getAttribute("content");
            if (contentValue != null && !contentValue.isBlank()) {
                targetMap.put(propertyKey, contentValue.strip());
            }
        }
    }

    private static void putMetaName(Page page, Map<String, String> targetMap, String nameKey) {
        Locator locator = page.locator("meta[name=\"" + nameKey + "\"]").first();
        if (locator.count() > 0) {
            String contentValue = locator.getAttribute("content");
            if (contentValue != null && !contentValue.isBlank()) {
                targetMap.put(nameKey, contentValue.strip());
            }
        }
    }

    private static void handleRoute(Route route) {
        String resourceType = route.request().resourceType();
        if ("document".equals(resourceType)) {
            route.resume();
            return;
        }
        if (BLOCKED_RESOURCE_TYPES.contains(resourceType)) {
            route.abort();
            return;
        }
        String requestUrl = route.request().url();
        if (shouldAbortByHost(requestUrl)) {
            route.abort();
            return;
        }
        route.resume();
    }

    private static boolean shouldAbortByHost(String urlString) {
        try {
            URI uri = URI.create(urlString);
            String host = uri.getHost();
            if (host == null) {
                return false;
            }
            String hostLower = host.toLowerCase(Locale.ROOT);
            for (String suffix : BLOCKED_HOST_SUFFIXES) {
                if (hostLower.equals(suffix) || hostLower.endsWith("." + suffix)) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }
}
