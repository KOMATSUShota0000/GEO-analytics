package com.geo.analytics.application.port;

/**
 * Optional HTTP headers for server-side Playwright to authenticate against the SPA and API
 * (mirrors {@code apiFetch}: {@code Authorization} + {@code X-Tenant-ID}).
 */
public record PdfBrowserAuthHeaders(String authorizationBearerValue, String tenantWorkspaceId) {

    public static final PdfBrowserAuthHeaders NONE = new PdfBrowserAuthHeaders(null, null);

    public boolean present() {
        return authorizationBearerValue != null
                && !authorizationBearerValue.isBlank()
                && tenantWorkspaceId != null
                && !tenantWorkspaceId.isBlank();
    }
}
