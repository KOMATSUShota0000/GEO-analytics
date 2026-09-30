package com.geo.analytics.domain.enums;

/**
 * 議論の発言が、前の発言に対してどんな立場で返しているか（#196）。
 *
 * <p>Why: 画面で「イノベーターへの反論」「条件つきで賛成」のように添えるため、自由記述ではなく列挙で返させる。
 * 「応答」は見本の画面（#199）の「指摘を受けて案を直す」発言に当たる。
 */
public enum DebateStance {
    REBUT("反論"),
    AGREE("賛成"),
    CONDITIONAL_AGREE("条件つきで賛成"),
    CONFIRM("指摘や数字の確認"),
    RESPOND("指摘を受けて案を直すなどの応答");

    private final String label;

    DebateStance(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
