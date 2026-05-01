package com.geo.analytics.application.dto;

/**
 * AI 解析のインプットとなる、ユーザーが入力した戦略的ナレッジ（自社の強み・ターゲット等）。
 * 未入力は空文字として扱う。{@code null} は受け付けず正規化する。
 */
public record UserStrategicKnowledge(String businessDescription, String targetAudience, String strategicFocus) {

    public UserStrategicKnowledge {
        businessDescription = businessDescription == null ? "" : businessDescription;
        targetAudience = targetAudience == null ? "" : targetAudience;
        strategicFocus = strategicFocus == null ? "" : strategicFocus;
    }
}
