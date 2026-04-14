package com.geo.analytics.application.port;

import com.geo.analytics.application.dto.PdfWhiteLabelInjection;
import java.util.UUID;

public interface PdfReportPort {
    byte[] renderJobReportPdf(UUID jobId);

    byte[] renderPrintRoutePdf(
            UUID jobId, String internalToken, PdfWhiteLabelInjection whiteLabel, PdfBrowserAuthHeaders browserAuth);
}
