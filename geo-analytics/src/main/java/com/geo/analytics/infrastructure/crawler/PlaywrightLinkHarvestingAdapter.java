package com.geo.analytics.infrastructure.crawler;

import com.geo.analytics.application.dto.DiscoveredLink;
import com.geo.analytics.application.port.LinkHarvestingPort;
import com.geo.analytics.infrastructure.crawler.playwright.PlaywrightBrowserLifecycle;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.Route;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Semaphore;
import org.springframework.stereotype.Component;

@Component
public class PlaywrightLinkHarvestingAdapter implements LinkHarvestingPort {

    private static final String CHROME_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final int PLAYWRIGHT_DEADLINE_MILLIS = 15_000;
    private static final int MAX_ANCHORS = 512;
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
            "quantserve.com");

    private final PlaywrightBrowserLifecycle playwrightBrowserLifecycle;
    private final Semaphore playwrightCrawlSemaphore;

    public PlaywrightLinkHarvestingAdapter(
            PlaywrightBrowserLifecycle playwrightBrowserLifecycle, Semaphore playwrightCrawlSemaphore) {
        this.playwrightBrowserLifecycle = playwrightBrowserLifecycle;
        this.playwrightCrawlSemaphore = playwrightCrawlSemaphore;
    }

    @Override
    public List<DiscoveredLink> harvestHtmlAnchorsSameOrigin(String pageUrl) {
        if (pageUrl == null || pageUrl.isBlank()) {
            throw new IllegalArgumentException("pageUrl");
        }
        String trimmedUrl = pageUrl.trim();
        Browser browser = playwrightBrowserLifecycle.getBrowser();
        if (browser == null) {
            return List.of();
        }
        boolean acquired = false;
        try {
            playwrightCrawlSemaphore.acquire();
            acquired = true;
            return harvestWithPlaywright(browser, trimmedUrl);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            return List.of();
        } catch (PlaywrightException playwrightException) {
            return List.of();
        } catch (RuntimeException runtimeException) {
            return List.of();
        } finally {
            if (acquired) {
                playwrightCrawlSemaphore.release();
            }
        }
    }

    private static List<DiscoveredLink> harvestWithPlaywright(Browser browser, String trimmedUrl) {
        BrowserContext browserContext =
                browser.newContext(new Browser.NewContextOptions().setUserAgent(CHROME_USER_AGENT));
        try {
            Page page = browserContext.newPage();
            try {
                page.setDefaultNavigationTimeout(PLAYWRIGHT_DEADLINE_MILLIS);
                page.setDefaultTimeout(PLAYWRIGHT_DEADLINE_MILLIS);
                page.route("**/*", PlaywrightLinkHarvestingAdapter::handleRoute);
                page.navigate(trimmedUrl, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
                page.waitForLoadState(LoadState.DOMCONTENTLOADED);
                try {
                    page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(5_000));
                } catch (PlaywrightException ignored) {
                }
                URI baseUri = URI.create(page.url());
                String entryHostCanon = canonicalHost(baseUri.getHost());
                List<Locator> locators = page.locator("a[href]").all();
                int cap = StrictMath.min(MAX_ANCHORS, locators.size());
                LinkedHashMap<String, DiscoveredLink> byUrlKey = new LinkedHashMap<>();
                for (int i = 0; i < cap; i++) {
                    Locator locator = locators.get(i);
                    String hrefRaw = locator.getAttribute("href");
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
                    String anchorText = anchorTextSafely(locator);
                    DiscoveredLink link = new DiscoveredLink(normalized.toString(), pathNorm, anchorText);
                    String dedupeKey = normalized.toString();
                    byUrlKey.putIfAbsent(dedupeKey, link);
                }
                return List.copyOf(new ArrayList<>(byUrlKey.values()));
            } finally {
                page.close();
            }
        } finally {
            browserContext.close();
        }
    }

    private static String anchorTextSafely(Locator locator) {
        try {
            String t = locator.textContent();
            return t == null ? "" : t.strip();
        } catch (RuntimeException runtimeException) {
            return "";
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
            for (int i = 0; i < BLOCKED_HOST_SUFFIXES.size(); i++) {
                String suffix = BLOCKED_HOST_SUFFIXES.get(i);
                if (hostLower.equals(suffix) || hostLower.endsWith("." + suffix)) {
                    return true;
                }
            }
        } catch (RuntimeException ignored) {
        }
        return false;
    }
}
