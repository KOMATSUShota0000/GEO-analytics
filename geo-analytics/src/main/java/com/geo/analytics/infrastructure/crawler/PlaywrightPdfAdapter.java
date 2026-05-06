package com.geo.analytics.infrastructure.crawler;
import com.geo.analytics.application.dto.PdfWhiteLabelInjection;
import com.geo.analytics.application.port.PdfBrowserAuthHeaders;
import com.geo.analytics.application.port.PdfReportPort;
import com.geo.analytics.application.service.PdfBrowserTokenIssuer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.Margin;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.microsoft.playwright.options.WaitUntilState;
import com.geo.analytics.infrastructure.config.AppProperties;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Semaphore;
@Component
public class PlaywrightPdfAdapter implements PdfReportPort {
    private static final Logger log = LoggerFactory.getLogger(PlaywrightPdfAdapter.class);
    private static final int STACK_TRACE_LIMIT = 20_000;
    private static final int MAX_CONCURRENT_PDF = 2;
    private static final int PDF_FLAG_TIMEOUT_MS = 20_000;
    private static final double PDF_CONTEXT_DEFAULT_TIMEOUT_MS = 120_000d;
    private static final double PDF_CONTEXT_NAVIGATION_TIMEOUT_MS = 90_000d;
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
          if (payload.pdfContextJson != null && String(payload.pdfContextJson).length > 0) {
            try {
              window.__GEO_PDF_CONTEXT__ = JSON.parse(payload.pdfContextJson);
            } catch (e) {
              window.__GEO_PDF_CONTEXT__ = null;
            }
          } else {
            window.__GEO_PDF_CONTEXT__ = null;
          }
        }
        """;
    private final Semaphore pdfSemaphore = new Semaphore(MAX_CONCURRENT_PDF, true);
    private final String pdfBaseUrl;
    private final ObjectMapper objectMapper;
    private final PdfBrowserTokenIssuer pdfBrowserTokenIssuer;
    public PlaywrightPdfAdapter(
            AppProperties appProperties,
            ObjectMapper objectMapper,
            PdfBrowserTokenIssuer pdfBrowserTokenIssuer) {
        String pdfBaseUrl = appProperties.getPdf().getBaseUrl();
        if (pdfBaseUrl == null || pdfBaseUrl.isBlank()) {
            throw new IllegalStateException("app.pdf.base-url must be configured");
        }
        this.pdfBaseUrl = pdfBaseUrl.replaceAll("/+$", "");
        this.objectMapper = objectMapper;
        this.pdfBrowserTokenIssuer = pdfBrowserTokenIssuer;
    }
    @Override
    public byte[] renderJobReportPdf(UUID jobId) {
        String pageUrl = pdfBaseUrl + "/job/" + jobId + "?pdf=1";
        try (SemaphoreLease _ = SemaphoreLease.acquire(pdfSemaphore);
                Playwright playwright = Playwright.create();
                Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true))) {
            try (BrowserContextLease contextLease = new BrowserContextLease(browser, PdfBrowserAuthHeaders.NONE)) {
                try (PageLease pageLease = new PageLease(contextLease.context().newPage())) {
                    return capturePdf(
                        pageLease.page(),
                        pageUrl,
                        new PdfWhiteLabelInjection("#4F46E5", "", "", ""));
                }
            }
        } catch (JsonProcessingException jsonProcessingException) {
            throw new IllegalStateException(jsonProcessingException);
        }
    }
    @Override
    public byte[] renderPrintRoutePdf(UUID jobId, String internalToken, PdfWhiteLabelInjection whiteLabel) {
        String encodedToken = URLEncoder.encode(internalToken, StandardCharsets.UTF_8);
        String pageUrl = pdfBaseUrl + "/reports/print/" + jobId + "?internal_token=" + encodedToken;
        PdfBrowserAuthHeaders browserAuth;
        try {
            browserAuth = pdfBrowserTokenIssuer.issueForCurrentTenant();
        } catch (RuntimeException runtimeException) {
            log.error(
                    "pdf_print_token_issue_failed jobId={} trace={}",
                    jobId,
                    truncateStackTrace(runtimeException));
            browserAuth = PdfBrowserAuthHeaders.NONE;
        }
        try (SemaphoreLease _ = SemaphoreLease.acquire(pdfSemaphore);
                Playwright playwright = Playwright.create();
                Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true))) {
            try (BrowserContextLease contextLease = new BrowserContextLease(browser, browserAuth)) {
                try (PageLease pageLease = new PageLease(contextLease.context().newPage())) {
                    return capturePdf(pageLease.page(), pageUrl, whiteLabel);
                }
            }
        } catch (JsonProcessingException jsonProcessingException) {
            throw new IllegalStateException(jsonProcessingException);
        }
    }
    private static void applyPdfBrowserAuth(BrowserContext context, PdfBrowserAuthHeaders auth) {
        if (!auth.present()) {
            return;
        }
        context.setExtraHTTPHeaders(
                Map.of(
                        "Authorization", auth.authorizationBearerValue(),
                        "X-Tenant-ID", auth.tenantWorkspaceId()));
        String raw = bearerRawToken(auth.authorizationBearerValue());
        String tenant = auth.tenantWorkspaceId();
        if (raw != null && !raw.isBlank() && tenant != null && !tenant.isBlank()) {
            context.addInitScript(pdfAuthBootstrapScript(raw, tenant));
        }
    }

    private static String pdfAuthBootstrapScript(String rawJwt, String workspaceTenantId) {
        String tokenLit = jsSingleQuotedLiteral(rawJwt);
        String tenantLit = jsSingleQuotedLiteral(workspaceTenantId);
        return "(() => {\n"
                + "  const token = "
                + tokenLit
                + ";\n"
                + "  const tenant = "
                + tenantLit
                + ";\n"
                + "  try { sessionStorage.setItem('geo_analytics.access_token', token); } catch (e) {}\n"
                + "  try { localStorage.setItem('geo_analytics.access_token', token); } catch (e) {}\n"
                + "  try { sessionStorage.setItem('geo_analytics.tenant_id', tenant); } catch (e) {}\n"
                + "  try { localStorage.setItem('geo_analytics.tenant_id', tenant); } catch (e) {}\n"
                + "  try {\n"
                + "    window.__PDF_AUTH_TOKEN__ = token;\n"
                + "    window.__PDF_TENANT_ID__ = tenant;\n"
                + "  } catch (e) {}\n"
                + "})();";
    }

    private static String jsSingleQuotedLiteral(String value) {
        if (value == null) {
            return "''";
        }
        String escaped =
                value.replace("\\", "\\\\")
                        .replace("'", "\\'")
                        .replace("\n", "\\n")
                        .replace("\r", "\\r");
        return "'" + escaped + "'";
    }

    private static String bearerRawToken(String authorizationHeader) {
        if (authorizationHeader == null) {
            return null;
        }
        String t = authorizationHeader.strip();
        int i = t.indexOf(' ');
        if (i < 0) {
            return null;
        }
        if (!t.regionMatches(true, 0, "Bearer", 0, 6)) {
            return null;
        }
        return t.substring(i + 1).strip();
    }

    private byte[] capturePdf(Page page, String pageUrl, PdfWhiteLabelInjection whiteLabel)
            throws JsonProcessingException {
        page.navigate(pageUrl, new Page.NavigateOptions().setWaitUntil(WaitUntilState.LOAD));
        page.setViewportSize(794, 1123);
        page.addStyleTag(
            new Page.AddStyleTagOptions().setContent(cssRootAndPage(whiteLabel.brandColor())));
        page.evaluate(WHITE_LABEL_INJECTION_SCRIPT, objectMapper.writeValueAsString(whiteLabel));
        page.waitForSelector(
            "#pdf-ready-flag",
            new Page.WaitForSelectorOptions()
                .setState(WaitForSelectorState.ATTACHED)
                .setTimeout(PDF_FLAG_TIMEOUT_MS));
        return page.pdf(
            new Page.PdfOptions()
                .setFormat("A4")
                .setPrintBackground(true)
                .setDisplayHeaderFooter(false)
                .setMargin(new Margin().setTop("0").setBottom("0").setLeft("0").setRight("0")));
    }
    private static String cssRootAndPage(String brandColor) {
        return ":root{--brand-color:"
            + sanitizeCssColorHex(brandColor)
            + ";}@page{size:A4 portrait;margin:0;}";
    }
    private static String sanitizeCssColorHex(String raw) {
        if (raw == null) {
            return "#4F46E5";
        }
        String t = raw.strip();
        if (t.matches("#[0-9A-Fa-f]{6}") || t.matches("#[0-9A-Fa-f]{3}")) {
            return t;
        }
        return "#4F46E5";
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
        BrowserContextLease(Browser browser, PdfBrowserAuthHeaders browserAuth) {
            this.browserContext = browser.newContext();
            this.browserContext.setDefaultTimeout(PDF_CONTEXT_DEFAULT_TIMEOUT_MS);
            this.browserContext.setDefaultNavigationTimeout(PDF_CONTEXT_NAVIGATION_TIMEOUT_MS);
            applyPdfBrowserAuth(this.browserContext, browserAuth);
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
