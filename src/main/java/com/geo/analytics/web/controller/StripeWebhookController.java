package com.geo.analytics.web.controller;

import com.geo.analytics.application.billing.StripeWebhookService;
import com.stripe.exception.SignatureVerificationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Stripe Webhook 受信口。{@code /api/public/**} 配下のため認証・CSRF・JWT・テナントヘッダの
 * いずれも免除される（既存のセキュリティ設定に準拠）。署名検証で正当性を担保する。
 * 生のボディ文字列で署名検証する必要があるため {@code @RequestBody String} で受ける。
 */
@RestController
@RequestMapping("/api/public/billing")
public class StripeWebhookController {
    private static final Logger LOG = LoggerFactory.getLogger(StripeWebhookController.class);

    private final StripeWebhookService webhookService;

    public StripeWebhookController(StripeWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> webhook(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String signature) {
        try {
            webhookService.handle(payload, signature);
            return ResponseEntity.ok("ok");
        } catch (SignatureVerificationException e) {
            LOG.warn("Stripe webhook signature verification failed");
            return ResponseEntity.badRequest().body("invalid signature");
        } catch (RuntimeException e) {
            LOG.warn("Stripe webhook handling failed", e);
            return ResponseEntity.badRequest().body("error");
        }
    }
}
