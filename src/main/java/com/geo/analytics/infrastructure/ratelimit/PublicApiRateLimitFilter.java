package com.geo.analytics.infrastructure.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 15)
public class PublicApiRateLimitFilter extends OncePerRequestFilter {

    private static final String BUCKET_KEY_PREFIX = "pub:";

    private final ProxyManager<String> proxyManager;
    private final ClientIpResolver clientIpResolver;
    private final long capacity;
    private final long refillMinutes;

    public PublicApiRateLimitFilter(
            @Qualifier("rateLimitProxyManager") ProxyManager<String> proxyManager,
            ClientIpResolver clientIpResolver,
            @Value("${app.rate-limit.public-api.capacity:10}") long capacity,
            @Value("${app.rate-limit.public-api.refill-minutes:1}") long refillMinutes) {
        this.proxyManager = proxyManager;
        this.clientIpResolver = clientIpResolver;
        this.capacity = capacity;
        this.refillMinutes = refillMinutes;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String p = stripContextPath(request);
        if (!p.startsWith("/api/public/")) {
            return true;
        }
        // Stripe Webhook は Stripe からのサーバー間通信で、署名検証で正当性を担保する。
        // 失敗時の再送（最大数十時間リトライ）やイベント集中で IP 単位のレート制限に達すると
        // 決済完了通知を取りこぼし、プラン昇格が反映されなくなるため、対象から除外する。
        return "/api/public/billing/webhook".equals(p);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String clientIp = clientIpResolver.resolve(request);
        String bucketKey = BUCKET_KEY_PREFIX + clientIp;

        BucketConfiguration configuration = BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(capacity)
                        .refillIntervally(capacity, Duration.ofMinutes(refillMinutes))
                        .build())
                .build();

        ConsumptionProbe probe = proxyManager.builder()
                .build(bucketKey, () -> configuration)
                .tryConsumeAndReturnRemaining(1L);

        if (!probe.isConsumed()) {
            long retryAfterSeconds = (probe.getNanosToWaitForRefill() + 999_999_999L) / 1_000_000_000L;
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
            response.sendError(HttpStatus.TOO_MANY_REQUESTS.value(), "Rate limit exceeded");
            return;
        }

        response.setHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
        filterChain.doFilter(request, response);
    }

    private static String stripContextPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String ctx = request.getContextPath();
        if (ctx != null && !ctx.isEmpty() && uri.startsWith(ctx)) {
            return uri.substring(ctx.length());
        }
        return uri;
    }
}
