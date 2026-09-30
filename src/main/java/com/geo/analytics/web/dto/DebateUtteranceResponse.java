package com.geo.analytics.web.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.geo.analytics.domain.model.DebateUtterance;

/** 結果画面に出す議論の1発言（#198）。発言者・立場・根拠の種類は列挙の名前で返し、表示名は画面で決める。 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record DebateUtteranceResponse(
        Integer round,
        String speaker,
        String summary,
        String replyTo,
        String stance,
        String evidenceKind,
        Integer evidenceTaskNumber,
        String evidenceDetail) {

    public static DebateUtteranceResponse from(DebateUtterance u) {
        return new DebateUtteranceResponse(
                u.round(),
                name(u.speaker()),
                u.summary(),
                name(u.replyTo()),
                name(u.stance()),
                name(u.evidenceKind()),
                u.evidenceTaskNumber(),
                u.evidenceDetail());
    }

    private static String name(Enum<?> value) {
        return value == null ? null : value.name();
    }
}
