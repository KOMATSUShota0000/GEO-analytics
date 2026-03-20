package com.geo.analytics.infrastructure.crawler;

import com.geo.analytics.application.port.PdfReportPort;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.Margin;
import com.microsoft.playwright.options.WaitUntilState;
import com.microsoft.playwright.options.WaitForSelectorState;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.UUID;
import java.util.concurrent.Semaphore;

@Component
public class PlaywrightPdfAdapter implements PdfReportPort {
    private static final int MAX_CONCURRENT_PDF = 3;

    private final Semaphore pdfSemaphore = new Semaphore(MAX_CONCURRENT_PDF);
    private final String pdfBaseUrl;
    private Playwright playwright;
    private Browser browser;

    public PlaywrightPdfAdapter(@Value("${app.pdf.base-url}") String pdfBaseUrl) {
        if (pdfBaseUrl == null || pdfBaseUrl.isBlank()) {
            throw new IllegalStateException("app.pdf.base-url must be configured");
        }
        this.pdfBaseUrl = pdfBaseUrl.replaceAll("/+$", "");
    }

    @PostConstruct
    void launchBrowser() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
    }

    @PreDestroy
    void shutdownBrowser() {
        if (browser != null) {
            browser.close();
        }
        if (playwright != null) {
            playwright.close();
        }
    }

    @Override
    public byte[] renderJobReportPdf(UUID jobId) {
        String pageUrl = pdfBaseUrl + "/job/" + jobId;
        try (SemaphoreLease ignored = SemaphoreLease.acquire(pdfSemaphore);
             BrowserContextLease contextLease = new BrowserContextLease(browser);
             PageLease pageLease = new PageLease(contextLease.context().newPage())) {
            Page page = pageLease.page();
            page.navigate(pageUrl, new Page.NavigateOptions().setWaitUntil(WaitUntilState.NETWORKIDLE));
            page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(60_000));
            page.waitForSelector("#pdf-ready-flag", new Page.WaitForSelectorOptions().setState(WaitForSelectorState.ATTACHED));
            Margin margin = new Margin()
                .setTop("15mm")
                .setBottom("15mm")
                .setLeft("15mm")
                .setRight("15mm");
            return page.pdf(
                new Page.PdfOptions()
                    .setFormat("A4")
                    .setPrintBackground(true)
                    .setMargin(margin));
        }
    }

    private static final class SemaphoreLease implements AutoCloseable {
        private final Semaphore semaphore;

        private SemaphoreLease(Semaphore semaphore) {
            this.semaphore = semaphore;
        }

        static SemaphoreLease acquire(Semaphore semaphore) {
            try {
                semaphore.acquire();
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(interruptedException);
            }
            return new SemaphoreLease(semaphore);
        }

        @Override
        public void close() {
            semaphore.release();
        }
    }

    private static final class BrowserContextLease implements AutoCloseable {
        private final BrowserContext browserContext;

        BrowserContextLease(Browser browser) {
            this.browserContext = browser.newContext();
        }

        BrowserContext context() {
            return browserContext;
        }

        @Override
        public void close() {
            browserContext.close();
        }
    }

    private static final class PageLease implements AutoCloseable {
        private final Page page;

        PageLease(Page page) {
            this.page = page;
        }

        Page page() {
            return page;
        }

        @Override
        public void close() {
            page.close();
        }
    }
}
