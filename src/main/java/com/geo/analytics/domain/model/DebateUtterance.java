package com.geo.analytics.domain.model;

import com.geo.analytics.domain.ai.DebatePersona;
import com.geo.analytics.domain.enums.DebateEvidenceKind;
import com.geo.analytics.domain.enums.DebateStance;

/**
 * 解析ごとの議論の、画面に出す1発言（#196）。
 *
 * <p>Why: 議論用の本文は長く専門用語も混ざるため、次の発言者とまとめ役にだけ渡し、ここには持たない。
 * 画面には、発言者が自分で書いた短い要約と、誰への返事か・根拠を出す。
 *
 * @param round              ラウンド（1始まり）。まとめ役の発言は、どのラウンドにも属さないため {@code null}
 * @param summary            画面用の要約。AI が書かなかったときは空文字
 * @param replyTo            誰の発言への返事か。返事でなければ {@code null}
 * @param stance             返事の立場。{@code replyTo} が無いときは {@code null}
 * @param evidenceKind       根拠にした材料。無ければ {@code null}
 * @param evidenceTaskNumber 根拠が改善タスクのときの番号（取り組む順で1始まり）。それ以外は {@code null}
 * @param evidenceDetail     根拠の中身（例: 社名が出た質問 2問 / 10問）。無ければ空文字
 */
public record DebateUtterance(
        Integer round,
        DebatePersona speaker,
        String summary,
        DebatePersona replyTo,
        DebateStance stance,
        DebateEvidenceKind evidenceKind,
        Integer evidenceTaskNumber,
        String evidenceDetail) {

    public static DebateUtterance summaryOnly(Integer round, DebatePersona speaker, String summary) {
        return new DebateUtterance(round, speaker, summary, null, null, null, null, "");
    }
}
