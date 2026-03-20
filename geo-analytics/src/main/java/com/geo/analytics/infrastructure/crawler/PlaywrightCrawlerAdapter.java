package com.geo.analytics.infrastructure.crawler;

import com.geo.analytics.application.dto.CrawledPageData;
import com.geo.analytics.application.port.WebCrawlerPort;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.Route;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.util.Set;

@Component
public class PlaywrightCrawlerAdapter implements WebCrawlerPort {
    private static final Set<String> BLOCKED_RESOURCE_TYPES = Set.of(
        "image",
        "stylesheet",
        "media",
        "font"
    );

    @Override
    public CrawledPageData extractContent(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("url must not be blank");
        }
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(
                new BrowserType.LaunchOptions().setHeadless(true));
            try {
                BrowserContext context = browser.newContext();
                try {
                    Page page = context.newPage();
                    try {
                        page.route("**/*", PlaywrightCrawlerAdapter::handleRoute);
                        page.navigate(url, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
                        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
                        try {
                            page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(15_000));
                        } catch (PlaywrightException ignored) {
                        }
                        String innerText = page.locator("body").innerText();
                        String normalized = innerText == null ? "" : innerText;
                        String contentHash = DigestUtils.sha256Hex(normalized.getBytes(StandardCharsets.UTF_8));
                        return new CrawledPageData(url, normalized, contentHash);
                    } finally {
                        page.close();
                    }
                } finally {
                    context.close();
                }
            } finally {
                browser.close();
            }
        }
    }

    private static void handleRoute(Route route) {
        String resourceType = route.request().resourceType();
        if (BLOCKED_RESOURCE_TYPES.contains(resourceType)) {
            route.abort();
        } else {
            route.resume();
        }
    }
}
