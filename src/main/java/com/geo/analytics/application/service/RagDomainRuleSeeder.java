package com.geo.analytics.application.service;

import com.geo.analytics.domain.entity.RagDomainRuleEntity;
import com.geo.analytics.domain.enums.RagDomainRuleKind;
import com.geo.analytics.infrastructure.repository.RagDomainRuleRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
@Order(5)
@ConditionalOnProperty(prefix = "app.bootstrap", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RagDomainRuleSeeder implements ApplicationRunner {
    private final RagDomainRuleRepository ragDomainRuleRepository;
    private final DomainTrustService domainTrustService;

    public RagDomainRuleSeeder(RagDomainRuleRepository ragDomainRuleRepository, DomainTrustService domainTrustService) {
        this.ragDomainRuleRepository = ragDomainRuleRepository;
        this.domainTrustService = domainTrustService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (ragDomainRuleRepository.count() > 0) {
            domainTrustService.refreshCache();
            return;
        }
        var chie = new RagDomainRuleEntity();
        chie.setHostSuffix("chiebukuro.yahoo.co.jp");
        chie.setRuleKind(RagDomainRuleKind.TRUST_BOOST);
        chie.setTrustBoost(1.25);
        chie.setActive(true);
        var pr = new RagDomainRuleEntity();
        pr.setHostSuffix("prtimes.jp");
        pr.setRuleKind(RagDomainRuleKind.TRUST_BOOST);
        pr.setTrustBoost(1.2);
        pr.setActive(true);
        var itr = new RagDomainRuleEntity();
        itr.setHostSuffix("itreview.jp");
        itr.setRuleKind(RagDomainRuleKind.TRUST_BOOST);
        itr.setTrustBoost(1.15);
        itr.setActive(true);
        ragDomainRuleRepository.saveAll(List.of(chie, pr, itr));
        domainTrustService.refreshCache();
    }
}
