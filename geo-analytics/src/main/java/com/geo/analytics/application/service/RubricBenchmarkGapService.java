package com.geo.analytics.application.service;

import com.geo.analytics.application.dto.RubricAuditResult;
import com.geo.analytics.application.dto.RubricItemAudit;
import com.geo.analytics.domain.enums.RubricCriterionId;
import com.geo.analytics.domain.enums.RubricVerdictStatus;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class RubricBenchmarkGapService {

    private static final List<String> EMPTY_GAPS = List.of();

    public List<String> extractGaps(RubricAuditResult self, List<RubricAuditResult> competitors) {
        if (self == null) {
            return EMPTY_GAPS;
        }
        List<RubricAuditResult> comps = competitors != null ? competitors : List.of();
        ArrayList<String> out = new ArrayList<>();
        List<RubricCriterionId> targets = RubricCriterionId.llmCriteria();
        for (int c = 0; c < targets.size(); c++) {
            RubricCriterionId criterionId = targets.get(c);
            RubricVerdictStatus selfStatus = findStatus(self, criterionId);
            if (selfStatus != RubricVerdictStatus.NO && selfStatus != RubricVerdictStatus.PARTIAL) {
                continue;
            }
            boolean anyYes = false;
            for (int i = 0; i < comps.size(); i++) {
                RubricAuditResult comp = comps.get(i);
                if (comp != null && findStatus(comp, criterionId) == RubricVerdictStatus.YES) {
                    anyYes = true;
                    break;
                }
            }
            if (anyYes) {
                out.add(criterionId.name());
            }
        }
        return List.copyOf(out);
    }

    private static RubricVerdictStatus findStatus(RubricAuditResult audit, RubricCriterionId criterionId) {
        List<RubricItemAudit> items = audit.items();
        for (int i = 0; i < items.size(); i++) {
            RubricItemAudit item = items.get(i);
            if (item.criterionId() == criterionId) {
                return item.status();
            }
        }
        return RubricVerdictStatus.NO;
    }
}
