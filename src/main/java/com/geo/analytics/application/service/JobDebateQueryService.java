package com.geo.analytics.application.service;

import com.geo.analytics.domain.entity.JobDebateUtteranceEntity;
import com.geo.analytics.domain.model.DebateUtterance;
import com.geo.analytics.infrastructure.repository.JobDebateUtteranceRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 結果画面に出す議論の発言を読む（#198）。
 *
 * <p>Why: 発言は RLS（組織ID）で隔離している。組織IDは、別のクラスから呼ばれた {@code @Transactional} の
 * メソッドでしか接続に渡らないため（R-02）、読み出しをこのサービスに分けて、コントローラーから呼ぶ。
 */
@Service
@Transactional(readOnly = true)
public class JobDebateQueryService {

    private final JobDebateUtteranceRepository repository;

    public JobDebateQueryService(JobDebateUtteranceRepository repository) {
        this.repository = repository;
    }

    /** 話した順。発言が無いジョブ・他社のジョブでは空。 */
    public List<DebateUtterance> findUtterances(UUID jobId) {
        return repository.findByJobIdOrderBySeqAsc(jobId).stream()
                .map(JobDebateUtteranceEntity::toUtterance)
                .toList();
    }
}
