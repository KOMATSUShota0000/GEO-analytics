package com.geo.analytics.application.port;

import com.geo.analytics.application.dto.MeoTrust;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface MEODataPort {

    Map<String, MeoTrust> fetchTrustForArea(UUID projectId, String searchQuery, List<String> targetUrls);
}
