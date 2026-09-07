package com.geo.analytics.domain.enums;

/**
 * 解析対象の事業形態。{@link IndustryType} を粗くグルーピングしたもので、
 * {@code GeoVisibilityCalculatorService} が権威スコアの配分（第三者言及と MEO の比重）を
 * 切り替えるために使う。
 *
 * <p>旧名 {@code CompetitorExtractionMode}。競合検索のモード指定として導入された経緯があるが、
 * 競合機能の廃止（ADR-031〜034）後も配分ロジックとして残ったため、実態に合わせて改名した。
 * 値そのものは変更していないので DB の値集合と CHECK 制約は据え置き。
 */
public enum BusinessModelType {
    LOCAL_STORE,
    CORPORATE_SERVICE,
    ONLINE_SERVICE
}
