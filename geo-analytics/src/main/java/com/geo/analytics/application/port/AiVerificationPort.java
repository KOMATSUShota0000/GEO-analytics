package com.geo.analytics.application.port;

import com.geo.analytics.application.dto.VerificationRequest;
import com.geo.analytics.application.dto.VerificationResponse;

public interface AiVerificationPort {
    VerificationResponse verify(VerificationRequest verificationRequest);
}
