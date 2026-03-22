package com.geo.analytics.infrastructure.crawler;

import com.geo.analytics.application.dto.PdfWhiteLabelInjection;
import com.geo.analytics.application.port.PdfReportPort;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.Margin;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.microsoft.playwright.options.WaitUntilState;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.Semaphore;

@Component
public class PlaywrightPdfAdapter implements PdfReportPort {
    private static final int MAX_CONCURRENT_PDF = 3;

    private static final String WHITE_LABEL_INJECTION_SCRIPT = """
        jsonString => {
          const payload = JSON.parse(jsonString);
          const root = document.documentElement;
          root.style.setProperty('--brand-color', payload.brandColor);
          let logoCss = 'none';
          if (payload.logoUrl && String(payload.logoUrl).length > 0) {
            const escaped = String(payload.logoUrl).replace(/\\\\/g, '\\\\\\\\').replace(/"/g, '\\\\"');
            logoCss = 'url("' + escaped + '")';
          }
          root.style.setProperty('--logo-url', logoCss);
          root.style.setProperty('--brand-name', payload.brandName != null ? String(payload.brandName) : '');
        }
        """;

    private final Semaphore pdfSemaphore = new Semaphore(MAX_CONCURRENT_PDF);
    private final String pdfBaseUrl;
    private final ObjectMapper objectMapper;
    private Playwright playwright;
    private Browser browser;

    public PlaywrightPdfAdapter(
            @Value("${app.pdf.base-url}") String pdfBaseUrl,
            ObjectMapper objectMapper) {
        if (pdfBaseUrl == null || pdfBaseUrl.isBlank()) {
            throw new IllegalStateException("app.pdf.base-url must be configured");
        }
        this.pdfBaseUrl = pdfBaseUrl.replaceAll("/+$", "");
        this.objectMapper = objectMapper;
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
        try (SemaphoreLease ignored = SemaphoreLease.acquire(pdfSemaphore)) {
            try (BrowserContextLease contextLease = new BrowserContextLease(browser)) {
                try (PageLease pageLease = new PageLease(contextLease.context().newPage())) {
                    Page page = pageLease.page();
                    page.navigate(pageUrl, new Page.NavigateOptions().setWaitUntil(WaitUntilState.NETWORKIDLE));
                    page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(60_000));
                    page.waitForSelector(
                        "#pdf-ready-flag",
                        new Page.WaitForSelectorOptions().setState(WaitForSelectorState.ATTACHED));
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
        }
    }

    @Override
    public byte[] renderPrintRoutePdf(UUID jobId, String internalToken, PdfWhiteLabelInjection whiteLabel) {
        String encodedToken = URLEncoder.encode(internalToken, StandardCharsets.UTF_8);
        String pageUrl = pdfBaseUrl + "/reports/print/" + jobId + "?internal_token=" + encodedToken;
        try (SemaphoreLease ignored = SemaphoreLease.acquire(pdfSemaphore)) {
            try (BrowserContextLease contextLease = new BrowserContextLease(browser)) {
                try (PageLease pageLease = new PageLease(contextLease.context().newPage())) {
                    Page page = pageLease.page();
                    page.navigate(pageUrl, new Page.NavigateOptions().setWaitUntil(WaitUntilState.NETWORKIDLE));
                    page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(60_000));
                    page.waitForSelector(
                        "#pdf-ready-flag",
                        new Page.WaitForSelectorOptions().setState(WaitForSelectorState.ATTACHED));
                    String whiteLabelJson;
                    try {
                        whiteLabelJson = objectMapper.writeValueAsString(whiteLabel);
                    } catch (JsonProcessingException jsonProcessingException) {
                        throw new IllegalStateException(jsonProcessingException);
                    }
                    page.evaluate(WHITE_LABEL_INJECTION_SCRIPT, whiteLabelJson);
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
