package com.geo.analytics.infrastructure.crawler.playwright;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.WaitUntilState;
import org.springframework.stereotype.Component;
import java.util.concurrent.Semaphore;

@Component
public class PlaywrightRenderedTextFetcher {

    private final PlaywrightBrowserLifecycle playwrightBrowserLifecycle;
    private final Semaphore playwrightCrawlSemaphore;

    public PlaywrightRenderedTextFetcher(
            PlaywrightBrowserLifecycle playwrightBrowserLifecycle,
            Semaphore playwrightCrawlSemaphore) {
        this.playwrightBrowserLifecycle = playwrightBrowserLifecycle;
        this.playwrightCrawlSemaphore = playwrightCrawlSemaphore;
    }

    public String fetchRenderedInnerText(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("url");
        }
        String trimmed = url.trim();
        boolean acquired = false;
        try {
            try {
                playwrightCrawlSemaphore.acquire();
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(interruptedException);
            }
            acquired = true;
            Browser browser = playwrightBrowserLifecycle.getBrowser();
            try (BrowserContext context = browser.newContext();
                    Page page = context.newPage()) {
                page.setDefaultNavigationTimeout(15000);
                try {
                    page.navigate(trimmed, new Page.NavigateOptions().setWaitUntil(WaitUntilState.NETWORKIDLE));
                } catch (TimeoutError timeoutError) {
                    page.navigate(trimmed, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
                }
                String text = page.locator("body").innerText();
                if (text == null || text.isEmpty()) {
                    return "";
                }
                return text.trim();
            }
        } finally {
            if (acquired) {
                playwrightCrawlSemaphore.release();
            }
        }
    }
}
