package com.geo.analytics.infrastructure.crawler.playwright;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

@Component
public class PlaywrightBrowserLifecycle {

    private Playwright playwright;
    private Browser browser;

    @PostConstruct
    public void start() {
        try {
            playwright = Playwright.create();
            browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        } catch (RuntimeException runtimeException) {
            throw new IllegalStateException(runtimeException);
        }
    }

    @PreDestroy
    public void stop() {
        if (browser != null) {
            browser.close();
            browser = null;
        }
        if (playwright != null) {
            playwright.close();
            playwright = null;
        }
    }

    public Browser getBrowser() {
        return browser;
    }
}
