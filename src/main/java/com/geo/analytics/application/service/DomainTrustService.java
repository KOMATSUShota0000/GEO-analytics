package com.geo.analytics.application.service;

import com.geo.analytics.domain.entity.RagDomainRuleEntity;
import com.geo.analytics.domain.enums.RagDomainRuleKind;
import com.geo.analytics.infrastructure.repository.RagDomainRuleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class DomainTrustService {
    private static final Logger log = LoggerFactory.getLogger(DomainTrustService.class);
    private static final double DEFAULT_TRUST = 1.0;
    private static final Set<String> TRANSLATION_HOST_MARKERS = Set.of(
        "translate.google",
        "deepl.com",
        "papago",
        "bing.com/translator",
        "translate.googleapis",
        "mymemory.translated",
        "libretranslate");
    private final RagDomainRuleRepository ragDomainRuleRepository;
    private final AtomicReference<List<RagDomainRuleEntity>> cachedRules = new AtomicReference<>(List.of());

    public DomainTrustService(RagDomainRuleRepository ragDomainRuleRepository) {
        this.ragDomainRuleRepository = ragDomainRuleRepository;
    }

    public void refreshCache() {
        cachedRules.set(ragDomainRuleRepository.findAllByActiveTrue());
    }

    private static String hostFromUrl(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        try {
            var u = url.trim();
            if (!u.contains("://")) {
                u = "https://" + u;
            }
            var uri = URI.create(u);
            var host = uri.getHost();
            return host != null ? host : "";
        } catch (Exception e) {
            return "";
        }
    }

    private boolean matchesTranslationHeuristic(String host) {
        for (var m : TRANSLATION_HOST_MARKERS) {
            if (host.contains(m)) {
                return true;
            }
        }
        return false;
    }

    private boolean isBlockedByRule(String host) {
        for (var r : cachedRules.get()) {
            if (r.getRuleKind() == RagDomainRuleKind.BLOCK_ANALYSIS && matchesHostSuffix(host, r.getHostSuffix())) {
                return true;
            }
        }
        return false;
    }

    private double resolveTrustBoost(String host) {
        var max = DEFAULT_TRUST;
        for (var r : cachedRules.get()) {
            if (r.getRuleKind() == RagDomainRuleKind.TRUST_BOOST && matchesHostSuffix(host, r.getHostSuffix())) {
                var b = r.getTrustBoost() != null ? r.getTrustBoost() : DEFAULT_TRUST;
                if (b > max) {
                    max = b;
                }
            }
        }
        return max;
    }

    private static boolean matchesHostSuffix(String host, String suffix) {
        if (suffix == null || suffix.isBlank()) {
            return false;
        }
        var s = suffix.toLowerCase(Locale.ROOT).trim();
        var h = host.toLowerCase(Locale.ROOT);
        return h.equals(s) || h.endsWith("." + s);
    }
}
