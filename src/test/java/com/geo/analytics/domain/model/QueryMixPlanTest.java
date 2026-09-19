package com.geo.analytics.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** #87: 指名検索を少数で頭打ちにし、残りを一般検索へ回す内訳を固定する。 */
class QueryMixPlanTest {

    @Test
    void プラン別の内訳() {
        assertThat(QueryMixPlan.forTotal(3)).isEqualTo(new QueryMixPlan(1, 2));    // STANDARD
        assertThat(QueryMixPlan.forTotal(10)).isEqualTo(new QueryMixPlan(2, 8));   // PRO
        assertThat(QueryMixPlan.forTotal(30)).isEqualTo(new QueryMixPlan(3, 27));  // EXPERT
    }

    @Test
    void 指名検索は3本で頭打ちになる() {
        assertThat(QueryMixPlan.forTotal(30).brandedCount()).isEqualTo(3);
        assertThat(QueryMixPlan.forTotal(100).brandedCount()).isEqualTo(3);
        assertThat(QueryMixPlan.forTotal(1000).brandedCount()).isEqualTo(3);
    }

    @Test
    void 本数が増えるほど一般検索へ回る() {
        assertThat(QueryMixPlan.forTotal(100).genericCount()).isEqualTo(97);
    }

    @Test
    void 少数でも指名検索が1本は確保される() {
        assertThat(QueryMixPlan.forTotal(2)).isEqualTo(new QueryMixPlan(1, 1));
        assertThat(QueryMixPlan.forTotal(3).brandedCount()).isEqualTo(1);
    }

    @Test
    void 一本しか撃てないなら改善余地のある一般検索へ回す() {
        assertThat(QueryMixPlan.forTotal(1)).isEqualTo(new QueryMixPlan(0, 1));
    }

    @Test
    void ゼロや負数でも一本は生成する() {
        assertThat(QueryMixPlan.forTotal(0).totalCount()).isEqualTo(1);
        assertThat(QueryMixPlan.forTotal(-5).totalCount()).isEqualTo(1);
    }

    @Test
    void 合計は常に指定本数と一致する() {
        for (int total = 1; total <= 50; total++) {
            assertThat(QueryMixPlan.forTotal(total).totalCount()).isEqualTo(total);
        }
    }

    @Test
    void 負の内訳は作れない() {
        assertThatThrownBy(() -> new QueryMixPlan(-1, 5)).isInstanceOf(IllegalArgumentException.class);
    }
}
