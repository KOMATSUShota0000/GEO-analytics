package com.geo.analytics.application.service;

import com.geo.analytics.domain.enums.BusinessModelType;
import com.geo.analytics.domain.enums.IndustryType;
import org.springframework.stereotype.Component;

@Component
public class IndustryTypeBusinessModelMapper {

    public BusinessModelType fromIndustryType(IndustryType industryType) {
        if (industryType == null) {
            return BusinessModelType.LOCAL_STORE;
        }
        return switch (industryType) {
            case LOCAL -> BusinessModelType.LOCAL_STORE;
            case EC -> BusinessModelType.ONLINE_SERVICE;
            case B2B -> BusinessModelType.CORPORATE_SERVICE;
            case B2C -> BusinessModelType.ONLINE_SERVICE;
            case YMYL -> BusinessModelType.LOCAL_STORE;
            case OTHER -> BusinessModelType.LOCAL_STORE;
        };
    }
}
