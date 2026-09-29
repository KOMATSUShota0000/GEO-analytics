package com.geo.analytics.web.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.geo.analytics.domain.exception.CheckoutUnavailableException;
import com.geo.analytics.web.dto.ApiErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerCheckoutTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void notConfigured_returns503_withCodeTheScreenUsesToAvoidAskingForRetry() {
        ResponseEntity<ApiErrorResponse> response = handler.handleCheckoutUnavailable(
                new CheckoutUnavailableException(CheckoutUnavailableException.Kind.NOT_CONFIGURED));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errorCode()).isEqualTo("billing_not_configured");
    }

    @Test
    void temporary_returns502_withRetryableCode() {
        ResponseEntity<ApiErrorResponse> response = handler.handleCheckoutUnavailable(
                new CheckoutUnavailableException(CheckoutUnavailableException.Kind.TEMPORARY));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errorCode()).isEqualTo("billing_temporarily_unavailable");
    }

    @Test
    void alreadySubscribed_returns409_withCodeTheScreenUsesToPointToInquiry() {
        ResponseEntity<ApiErrorResponse> response = handler.handleCheckoutUnavailable(
                new CheckoutUnavailableException(CheckoutUnavailableException.Kind.ALREADY_SUBSCRIBED));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errorCode()).isEqualTo("billing_already_subscribed");
    }
}
