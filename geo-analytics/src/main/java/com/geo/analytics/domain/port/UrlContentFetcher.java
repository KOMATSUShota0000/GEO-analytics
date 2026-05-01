package com.geo.analytics.domain.port;

import com.geo.analytics.domain.exception.ScrapingException;

/**
 * ドメインがインフラに課す、URL 由来のクリーンな本文テキスト取得の契約。
 */
public interface UrlContentFetcher {

    /**
     * 指定された URL から HTML を取得し、ノイズを除去した純粋なテキストを返す。
     *
     * @param url 取得対象の URL
     * @return クリーニング後の本文テキスト
     * @throws ScrapingException 取得またはテキスト化に失敗した場合
     */
    String fetchCleanText(String url);
}
