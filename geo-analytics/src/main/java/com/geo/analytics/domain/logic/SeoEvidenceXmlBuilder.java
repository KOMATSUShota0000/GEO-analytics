package com.geo.analytics.domain.logic;

import com.geo.analytics.domain.model.SeoEvidence;
import java.util.List;

/**
 * 競合SEOスニペットを XML でラップし、テキストノードをエスケープしてプロンプト汚染を抑える。
 */
public final class SeoEvidenceXmlBuilder {

    private SeoEvidenceXmlBuilder() {}

    /**
     * {@code evidences} が null または空のときは空文字。それ以外は
     *
     * <pre>
     * &lt;competitor_seo_data&gt;
     *   &lt;evidence&gt;
     *     &lt;url&gt;...&lt;/url&gt;
     *     &lt;title&gt;...&lt;/title&gt;
     *     &lt;snippet&gt;...&lt;/snippet&gt;
     *   &lt;/evidence&gt;
     * &lt;/competitor_seo_data&gt;
     * </pre>
     */
    public static String buildCompetitorBlock(List<SeoEvidence> evidences) {
        if (evidences == null || evidences.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(evidences.size() * 256 + 64);
        sb.append("<competitor_seo_data>\n");
        for (SeoEvidence e : evidences) {
            if (e == null) {
                continue;
            }
            sb.append("  <evidence>\n");
            sb.append("    <url>").append(escapeXml(e.url())).append("</url>\n");
            sb.append("    <title>").append(escapeXml(e.title())).append("</title>\n");
            sb.append("    <snippet>").append(escapeXml(e.snippet())).append("</snippet>\n");
            sb.append("  </evidence>\n");
        }
        sb.append("</competitor_seo_data>");
        return sb.toString();
    }

    /**
     * XML テキストノード用エスケープ（{@code null} は空文字）。
     */
    public static String escapeXml(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(raw.length() + 16);
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&apos;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }
}
