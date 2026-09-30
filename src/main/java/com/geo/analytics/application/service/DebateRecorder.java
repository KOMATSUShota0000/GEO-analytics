package com.geo.analytics.application.service;

import com.geo.analytics.domain.model.DebateUtterance;

/**
 * 解析ごとの議論の発言を、できた順に受け取る（#197）。
 *
 * <p>Why: {@link DebateAdviceGeneratorService} は DB を持たない。保存は呼び出し元（{@link GapAnalysisService}）に任せ、
 * 依存の向きを変えない。実装は例外を投げないこと。保存に失敗しても、議論と総合診断は止めない。
 */
public interface DebateRecorder {

    DebateRecorder NONE = new DebateRecorder() {
        @Override
        public void spoke(DebateUtterance utterance) {}

        @Override
        public void failed() {}
    };

    void spoke(DebateUtterance utterance);

    /** 議論が失敗し、チケットを返して議論なしの総合診断に切り替えたとき。 */
    void failed();
}
