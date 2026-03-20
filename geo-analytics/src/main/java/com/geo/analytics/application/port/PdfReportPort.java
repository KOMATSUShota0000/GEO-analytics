package com.geo.analytics.application.port;

import java.util.UUID;

public interface PdfReportPort {
    byte[] renderJobReportPdf(UUID jobId);
}
