package com.geo.analytics.web.controller;

import com.geo.analytics.application.billing.StripeCheckoutService;
import com.geo.analytics.infrastructure.tenant.TenantContextHolder;
import com.geo.analytics.web.dto.CheckoutUrlResponse;
import com.geo.analytics.web.dto.CreateCheckoutRequest;
import com.stripe.exception.StripeException;
import jakarta.validation.Valid;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 認証済みユーザー向けの Stripe Checkout 起動エンドポイント。
 * 現在のワークスペース（テナント）に対してサブスク購入セッションを生成し、リダイレクト先URLを返す。
 */
@RestController
@RequestMapping("/api/v1/billing")
public class BillingCheckoutController {
    private static final Logger LOG = LoggerFactory.getLogger(BillingCheckoutController.class);

    private final StripeCheckoutService checkoutService;

    public BillingCheckoutController(StripeCheckoutService checkoutService) {
        this.checkoutService = checkoutService;
    }

    @PostMapping("/checkout")
    public ResponseEntity<CheckoutUrlResponse> createCheckout(
            @RequestBody @Valid CreateCheckoutRequest request) {
        UUID workspaceId = TenantContextHolder.getTenantId()
                .orElseThrow(() -> new IllegalStateException("workspace tenant is not bound"));
        try {
            String url = checkoutService.createCheckoutUrl(workspaceId, request.plan());
            return ResponseEntity.ok(new CheckoutUrlResponse(url));
        } catch (StripeException e) {
            LOG.warn("Stripe checkout session creation failed for workspace {}", workspaceId, e);
            return ResponseEntity.status(502).build();
        }
    }
}
