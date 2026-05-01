package com.geo.analytics.infrastructure.security;

import com.geo.analytics.domain.exception.ScrapingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * SSRF 防止のため、名前解決を行わずに URL の危険な宛先（プライベート／ループバック等）を拒否する。
 */
@Component
public class SsrfValidator {

    private static final char FULLWIDTH_DOT = '\uFF0E';

    private static final char FULLWIDTH_COLON = '\uFF1A';

    /**
     * 各オクテットが十進 0–255 のみ（先頭ゼロ付きの曖昧表記は許容しつつ数値範囲で検証）。
     */
    private static final Pattern IPV4_DECIMAL = Pattern.compile(
            "^((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$");

    private static final Pattern HOST_CHARSET = Pattern.compile("^[a-z0-9.:\\-\\[\\]]+$");

    private static final Pattern DIGITS_AND_DOTS_ONLY = Pattern.compile("^[0-9.]+$");

    private static final Pattern DIGITS_ONLY = Pattern.compile("^[0-9]+$");

    public void validate(String targetUrl) {
        if (targetUrl == null || targetUrl.isBlank()) {
            throw new ScrapingException("URL must not be null or blank");
        }
        URI uri;
        try {
            uri = new URI(targetUrl);
        } catch (URISyntaxException e) {
            throw new ScrapingException("Invalid URL syntax", e);
        }
        if (uri.getUserInfo() != null) {
            throw new ScrapingException("User info is not allowed");
        }
        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            throw new ScrapingException("Invalid scheme");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new ScrapingException("Host is required");
        }
        String normalized = normalizeHost(host);
        assertHostCharset(normalized);
        String work = unwrapIpv6Brackets(normalized);
        assertHostCharset(work);

        if (isBlockedHost(work)) {
            throw new ScrapingException("Private IP access is blocked");
        }
    }

    private static String normalizeHost(String host) {
        StringBuilder sb = new StringBuilder(host.length());
        for (int i = 0; i < host.length(); i++) {
            char ch = host.charAt(i);
            if (ch == FULLWIDTH_DOT) {
                sb.append('.');
            } else if (ch == FULLWIDTH_COLON) {
                sb.append(':');
            } else {
                sb.append(ch);
            }
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    private static void assertHostCharset(String host) {
        if (host.isEmpty() || !HOST_CHARSET.matcher(host).matches()) {
            throw new ScrapingException("Invalid host format");
        }
    }

    /**
     * {@code [ipv6]} 形式のホストを内側の文字列に展開する。角括弧が無ければそのまま返す。入れ子は外側から繰り返し剥がす。
     */
    private static String unwrapIpv6Brackets(String host) {
        String h = host;
        while (h.length() >= 2 && h.charAt(0) == '[' && h.charAt(h.length() - 1) == ']') {
            h = h.substring(1, h.length() - 1);
        }
        return h;
    }

    private static boolean isBlockedHost(String h) {
        if (h.equals("localhost")) {
            return true;
        }
        String embeddedV4 = extractEmbeddedIpv4(h);
        if (embeddedV4 != null) {
            if (!IPV4_DECIMAL.matcher(embeddedV4).matches()) {
                throw new ScrapingException("Invalid IP format");
            }
            return isBlockedIpv4Octets(parseIpv4Octets(embeddedV4));
        }
        if (DIGITS_AND_DOTS_ONLY.matcher(h).matches()) {
            if (DIGITS_ONLY.matcher(h).matches()) {
                throw new ScrapingException("Invalid IP format");
            }
            if (!IPV4_DECIMAL.matcher(h).matches()) {
                throw new ScrapingException("Invalid IP format");
            }
            return isBlockedIpv4Octets(parseIpv4Octets(h));
        }
        if (h.contains(":")) {
            int[] expanded = tryExpandIpv6(stripIpv6ZoneId(h));
            if (expanded == null) {
                throw new ScrapingException("Invalid host format");
            }
            boolean allZero = true;
            for (int x : expanded) {
                if (x != 0) {
                    allZero = false;
                    break;
                }
            }
            if (allZero) {
                return true;
            }
            int h0 = expanded[0];
            if (h0 >= 0xFE80 && h0 <= 0xFEBF) {
                return true;
            }
            if (expanded[7] != 1) {
                return false;
            }
            for (int j = 0; j < 7; j++) {
                if (expanded[j] != 0) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    /**
     * {@code ::ffff:192.0.2.1} または末尾 {@code :x.x.x.x} の IPv4 部分を返す。該当しなければ null。
     */
    private static String extractEmbeddedIpv4(String h) {
        String hl = h.toLowerCase(Locale.ROOT);
        if (hl.startsWith("::ffff:")) {
            return h.substring(7);
        }
        if (!h.contains(".")) {
            return null;
        }
        int i = h.lastIndexOf(':');
        if (i < 0) {
            return null;
        }
        return h.substring(i + 1);
    }

    private static int[] parseIpv4Octets(String dottedDecimal) {
        String[] p = dottedDecimal.split("\\.", 4);
        return new int[] {
            Integer.parseInt(p[0]),
            Integer.parseInt(p[1]),
            Integer.parseInt(p[2]),
            Integer.parseInt(p[3])
        };
    }

    private static boolean isBlockedIpv4Octets(int a, int b, int c, int d) {
        if (a == 127) {
            return true;
        }
        if (a == 10) {
            return true;
        }
        if (a == 0 && b == 0 && c == 0 && d == 0) {
            return true;
        }
        if (a == 192 && b == 168) {
            return true;
        }
        if (a == 169 && b == 254) {
            return true;
        }
        return a == 172 && b >= 16 && b <= 31;
    }

    private static boolean isBlockedIpv4Octets(int[] o) {
        return isBlockedIpv4Octets(o[0], o[1], o[2], o[3]);
    }

    private static String stripIpv6ZoneId(String host) {
        int pct = host.indexOf('%');
        if (pct < 0) {
            return host;
        }
        return host.substring(0, pct);
    }

    /**
     * RFC 5952 風の省略なし／単一 {@code ::} のみの IPv6 を 8 つの 16-bit 値に展開する。ドット含みは呼び出し側で除外する。
     */
    private static int[] tryExpandIpv6(String host) {
        if (host.contains(".")) {
            return null;
        }
        String h = host.toLowerCase(Locale.ROOT);
        int cc = 0;
        int pos = -1;
        for (int i = 0; i < h.length() - 1; i++) {
            if (h.charAt(i) == ':' && h.charAt(i + 1) == ':') {
                cc++;
                pos = i;
            }
        }
        try {
            if (cc == 0) {
                String[] parts = h.split(":", -1);
                if (parts.length != 8) {
                    return null;
                }
                int[] v = new int[8];
                for (int i = 0; i < 8; i++) {
                    if (parts[i].isEmpty() || parts[i].length() > 4) {
                        return null;
                    }
                    v[i] = Integer.parseInt(parts[i], 16);
                }
                return v;
            }
            if (cc != 1) {
                return null;
            }
            String left = h.substring(0, pos);
            String right = h.substring(pos + 2);
            String[] leftParts = left.isEmpty() ? new String[0] : left.split(":", -1);
            String[] rightParts = right.isEmpty() ? new String[0] : right.split(":", -1);
            for (String p : leftParts) {
                if (p.isEmpty() || p.length() > 4) {
                    return null;
                }
            }
            for (String p : rightParts) {
                if (p.isEmpty() || p.length() > 4) {
                    return null;
                }
            }
            if (leftParts.length + rightParts.length >= 8) {
                return null;
            }
            int missing = 8 - leftParts.length - rightParts.length;
            int[] v = new int[8];
            int idx = 0;
            for (String p : leftParts) {
                v[idx++] = Integer.parseInt(p, 16);
            }
            for (int j = 0; j < missing; j++) {
                v[idx++] = 0;
            }
            for (String p : rightParts) {
                v[idx++] = Integer.parseInt(p, 16);
            }
            return v;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
