package com.geo.analytics.web.controller;

import com.geo.analytics.application.billing.StripeCheckoutService;
import com.geo.analytics.infrastructure.tenant.TenantContextHolder;
import com.geo.analytics.web.dto.CheckoutUrlResponse;
import com.geo.analytics.web.dto.CreateCheckoutRequest;
import jakarta.validation.Valid;
import java.util.UUID;
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
    private final StripeCheckoutService checkoutService;

    public BillingCheckoutController(StripeCheckoutService checkoutService) {
        this.checkoutService = checkoutService;
    }

    @PostMapping("/checkout")
    public ResponseEntity<CheckoutUrlResponse> createCheckout(
            @RequestBody @Valid CreateCheckoutRequest request) {
        UUID workspaceId = TenantContextHolder.getTenantId()
                .orElseThrow(() -> new IllegalStateException("workspace tenant is not bound"));
        String url = checkoutService.createCheckoutUrl(workspaceId, request.plan());
        return ResponseEntity.ok(new CheckoutUrlResponse(url));
    }
}
