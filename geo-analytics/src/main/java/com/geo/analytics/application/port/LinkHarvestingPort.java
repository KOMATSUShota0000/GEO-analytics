package com.geo.analytics.application.port;

import com.geo.analytics.application.dto.DiscoveredLink;
import java.util.List;

public interface LinkHarvestingPort {
    List<DiscoveredLink> harvestHtmlAnchorsSameOrigin(String pageUrl);
}
