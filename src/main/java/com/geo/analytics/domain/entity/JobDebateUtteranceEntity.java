package com.geo.analytics.domain.entity;

import com.geo.analytics.domain.ai.DebatePersona;
import com.geo.analytics.domain.enums.DebateEvidenceKind;
import com.geo.analytics.domain.enums.DebateStance;
import com.geo.analytics.domain.model.DebateUtterance;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/**
 * 議論の1発言（V146）。画面側は読むだけで、書くのは裏の処理（{@code BatchPersistenceService}）。
 *
 * <p>Why: 他社の発言が見えないことは RLS（組織ID）で守る。{@code api_worker} には SELECT しか許していない。
 */
@Entity
@Immutable
@Table(name = "job_debate_utterances")
public class JobDebateUtteranceEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "job_id")
    private UUID jobId;

    @Column(name = "seq")
    private short seq;

    @Column(name = "round")
    private Short round;

    @Enumerated(EnumType.STRING)
    @Column(name = "speaker", length = 16)
    private DebatePersona speaker;

    @Column(name = "summary")
    private String summary;

    @Enumerated(EnumType.STRING)
    @Column(name = "reply_to", length = 16)
    private DebatePersona replyTo;

    @Enumerated(EnumType.STRING)
    @Column(name = "stance", length = 32)
    private DebateStance stance;

    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_kind", length = 32)
    private DebateEvidenceKind evidenceKind;

    @Column(name = "evidence_task_number")
    private Short evidenceTaskNumber;

    @Column(name = "evidence_detail")
    private String evidenceDetail;

    protected JobDebateUtteranceEntity() {}

    public DebateUtterance toUtterance() {
        return new DebateUtterance(
                round == null ? null : round.intValue(),
                speaker,
                summary,
                replyTo,
                stance,
                evidenceKind,
                evidenceTaskNumber == null ? null : evidenceTaskNumber.intValue(),
                evidenceDetail);
    }
}
