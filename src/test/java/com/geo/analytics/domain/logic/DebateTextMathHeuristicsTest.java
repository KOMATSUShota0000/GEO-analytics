package com.geo.analytics.domain.logic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

import org.junit.jupiter.api.Test;

class DebateTextMathHeuristicsTest {

    @Test
    void confidenceSumIsOneAndCentroidSumIsOne() {
        String page = "導入事例が2023年に100件を超え、サービス提供を開始。";
        String a = "事実1: 数値A\n- 箇条書きB";
        String n = "[引用: 事実1] 独自の仮説です。";
        String s = "一般論の飛躍があり、同業者にも当てはまる。";
        var r = DebateTextMathHeuristics.compute(page, a, n, s);
        double sConf = 0.0d;
        for (double c : r.currConfidences()) {
            sConf += c;
        }
        assertThat(sConf).isCloseTo(1.0d, offset(1.0e-6d));
        double sCent = 0.0d;
        for (double t : r.currCentroid()) {
            sCent += t;
        }
        assertThat(sCent).isCloseTo(1.0d, offset(1.0e-6d));
    }

    @Test
    void pMarketConstantLength4() {
        assertThat(DebateTextMathHeuristics.P_MARKET_UNIFORM_4).hasSize(4);
    }
}
