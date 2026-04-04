package com.geo.analytics.domain.matching;

import java.lang.StrictMath;

public final class PhoneticBitEncoder {

    private static final char[] HW_TO_FULL = {
            '\u30A2', '\u30A4', '\u30A6', '\u30A8', '\u30AA',
            '\u30AB', '\u30AD', '\u30AF', '\u30B1', '\u30B3', '\u30B5', '\u30B7', '\u30B9', '\u30BB', '\u30BD', '\u30BF', '\u30C1', '\u30C3', '\u30C6', '\u30C8',
            '\u30CA', '\u30CB', '\u30CC', '\u30CD', '\u30CE',
            '\u30CF', '\u30D2', '\u30D5', '\u30D8', '\u30DB',
            '\u30DE', '\u30DF', '\u30E0', '\u30E1', '\u30E2',
            '\u30E4', '\u30E6', '\u30E8',
            '\u30E9', '\u30EA', '\u30EB', '\u30EC', '\u30ED', '\u30EF', '\u30F3'
    };

    private PhoneticBitEncoder() {
    }

    public static int encode(CharSequence input, char[] romajiBuffer, char[] primaryScratch, char[] secondaryScratch, int[] metaLengths) {
        if (romajiBuffer == null || primaryScratch == null || secondaryScratch == null || metaLengths == null || metaLengths.length < 2) {
            throw new IllegalArgumentException();
        }
        int rl = transliterateToRomajiLower(input, romajiBuffer, romajiBuffer.length);
        asciiUppercaseInPlace(romajiBuffer, rl);
        DoubleMetaphoneCharEngine.encode(romajiBuffer, rl, primaryScratch, secondaryScratch, metaLengths);
        int p = packPhoneticHalf(primaryScratch, metaLengths[0]);
        int s = packPhoneticHalf(secondaryScratch, metaLengths[1]);
        return (p << 16) | (s & 0xFFFF);
    }

    private static int packPhoneticHalf(char[] buf, int len) {
        int lim = (int) StrictMath.min(len, 3);
        int v = 0;
        for (int i = 0; i < lim; i++) {
            v = v * 28 + phoneticSymbol(buf[i]);
        }
        for (int i = lim; i < 3; i++) {
            v *= 28;
        }
        return v & 0xFFFF;
    }

    private static int phoneticSymbol(char c) {
        if (c == ' ') {
            return 0;
        }
        if (c == '0') {
            return 27;
        }
        if (c >= 'A' && c <= 'Z') {
            return c - 'A' + 1;
        }
        return 0;
    }

    private static void asciiUppercaseInPlace(char[] buf, int len) {
        for (int i = 0; i < len; i++) {
            char c = buf[i];
            if (c >= 'a' && c <= 'z') {
                buf[i] = (char) (c - 32);
            }
        }
    }

    private static char normalizeKana(char ch) {
        if (ch >= '\u3041' && ch <= '\u3096') {
            return (char) (ch + 0x60);
        }
        if (ch >= '\uFF66' && ch <= '\uFF70') {
            switch (ch) {
                case '\uFF66':
                    return '\u30F2';
                case '\uFF67':
                    return '\u30A1';
                case '\uFF68':
                    return '\u30A3';
                case '\uFF69':
                    return '\u30A5';
                case '\uFF6A':
                    return '\u30A7';
                case '\uFF6B':
                    return '\u30A9';
                case '\uFF6C':
                    return '\u30E3';
                case '\uFF6D':
                    return '\u30E5';
                case '\uFF6E':
                    return '\u30E7';
                case '\uFF6F':
                    return '\u30C3';
                case '\uFF70':
                    return '\u30FC';
                default:
                    return ch;
            }
        }
        if (ch >= '\uFF71' && ch <= '\uFF9D') {
            int idx = ch - '\uFF71';
            if (idx >= 0 && idx < HW_TO_FULL.length) {
                return HW_TO_FULL[idx];
            }
        }
        return ch;
    }

    private static boolean isSmallKana(char ch) {
        return ch == '\u30A1' || ch == '\u30A3' || ch == '\u30A5' || ch == '\u30A7' || ch == '\u30A9'
                || ch == '\u30E3' || ch == '\u30E5' || ch == '\u30E7';
    }

    private static int transliterateToRomajiLower(CharSequence in, char[] out, int maxOut) {
        int w = 0;
        char lastRomajiVowel = 'a';
        boolean geminate = false;
        int n = in.length();
        for (int i = 0; i < n && w < maxOut; ) {
            char raw = in.charAt(i);
            char ch = normalizeKana(raw);
            if (raw >= 'A' && raw <= 'Z') {
                out[w++] = (char) (raw + 32);
                lastRomajiVowel = vowelFromAscii(out[w - 1]);
                i++;
                continue;
            }
            if (raw >= 'a' && raw <= 'z') {
                out[w++] = raw;
                lastRomajiVowel = vowelFromAscii(raw);
                i++;
                continue;
            }
            if (ch == '\u30FC') {
                if (lastRomajiVowel != 0 && w < maxOut) {
                    out[w++] = lastRomajiVowel;
                }
                i++;
                continue;
            }
            if (ch == '\u30C3' || ch == '\u3063') {
                geminate = true;
                i++;
                continue;
            }
            char sm = 0;
            if (i + 1 < n) {
                char nx = normalizeKana(in.charAt(i + 1));
                if (isSmallKana(nx)) {
                    sm = nx;
                }
            }
            int step = sm == 0 ? 1 : 2;
            int nw = emitKatakanaSyllable(ch, sm, geminate, out, w, maxOut);
            geminate = false;
            if (nw > w) {
                for (int j = w; j < nw; j++) {
                    char cj = out[j];
                    if (cj == 'a' || cj == 'i' || cj == 'u' || cj == 'e' || cj == 'o') {
                        lastRomajiVowel = cj;
                    }
                }
                w = nw;
            }
            i += step;
        }
        return w;
    }

    private static char vowelFromAscii(char c) {
        if (c == 'a' || c == 'i' || c == 'u' || c == 'e' || c == 'o') {
            return c;
        }
        return 0;
    }

    private static int emitKatakanaSyllable(char k, char small, boolean geminate, char[] out, int o, int max) {
        if (o >= max) {
            return o;
        }
        int oo = o;
        if (geminate) {
            char cons = geminateLeadConsonant(k, small);
            if (cons != 0 && oo < max) {
                out[oo++] = cons;
            }
        }
        return emitBody(k, small, out, oo, max);
    }

    private static char geminateLeadConsonant(char k, char small) {
        if (small != 0) {
            switch (k) {
                case '\u30AD':
                    return 'k';
                case '\u30AE':
                    return 'g';
                case '\u30B7':
                case '\u30B8':
                    return 's';
                case '\u30C1':
                case '\u30C2':
                    return 't';
                case '\u30C4':
                case '\u30C5':
                    return 't';
                case '\u30CB':
                    return 'n';
                case '\u30D2':
                case '\u30D3':
                case '\u30D4':
                    return 'h';
                case '\u30DF':
                    return 'm';
                case '\u30EA':
                    return 'r';
                case '\u30C6':
                case '\u30C7':
                    return 't';
                default:
                    break;
            }
        }
        switch (k) {
            case '\u30AB':
            case '\u30AC':
                return 'k';
            case '\u30AD':
            case '\u30AE':
                return 'k';
            case '\u30AF':
            case '\u30B0':
                return 'k';
            case '\u30B1':
            case '\u30B2':
                return 'k';
            case '\u30B3':
            case '\u30B4':
                return 'k';
            case '\u30B5':
            case '\u30B6':
                return 's';
            case '\u30B7':
            case '\u30B8':
                return 's';
            case '\u30B9':
            case '\u30BA':
                return 's';
            case '\u30BB':
            case '\u30BC':
                return 's';
            case '\u30BD':
            case '\u30BE':
                return 's';
            case '\u30BF':
            case '\u30C0':
                return 't';
            case '\u30C1':
            case '\u30C2':
                return 't';
            case '\u30C3':
            case '\u30C4':
            case '\u30C5':
                return 't';
            case '\u30C6':
            case '\u30C7':
                return 't';
            case '\u30C8':
            case '\u30C9':
                return 't';
            case '\u30CA':
            case '\u30CB':
            case '\u30CC':
            case '\u30CD':
            case '\u30CE':
                return 'n';
            case '\u30CF':
            case '\u30D2':
            case '\u30D5':
            case '\u30D8':
            case '\u30DB':
                return 'h';
            case '\u30D0':
            case '\u30D3':
            case '\u30D6':
            case '\u30D9':
            case '\u30DC':
                return 'b';
            case '\u30D1':
            case '\u30D4':
            case '\u30D7':
            case '\u30DA':
            case '\u30DD':
                return 'p';
            case '\u30DE':
            case '\u30DF':
            case '\u30E0':
            case '\u30E1':
            case '\u30E2':
                return 'm';
            case '\u30E4':
            case '\u30E6':
            case '\u30E8':
                return 'y';
            case '\u30E9':
            case '\u30EA':
            case '\u30EB':
            case '\u30EC':
            case '\u30ED':
                return 'r';
            case '\u30EF':
            case '\u30F2':
                return 'w';
            case '\u30F4':
                return 'v';
            case '\u30F3':
                return 'n';
            default:
                return 0;
        }
    }

    private static int emitBody(char k, char small, char[] out, int o, int max) {
        if (small == 0) {
            return emitSingle(k, out, o, max);
        }
        return emitCombo(k, small, out, o, max);
    }

    private static int emitSingle(char k, char[] out, int o, int max) {
        switch (k) {
            case '\u30A1':
                return put(out, o, max, 'a');
            case '\u30A2':
                return put(out, o, max, 'a');
            case '\u30A3':
                return put(out, o, max, 'i');
            case '\u30A4':
                return put(out, o, max, 'i');
            case '\u30A5':
                return put(out, o, max, 'u');
            case '\u30A6':
                return put(out, o, max, 'u');
            case '\u30A7':
                return put(out, o, max, 'e');
            case '\u30A8':
                return put(out, o, max, 'e');
            case '\u30A9':
                return put(out, o, max, 'o');
            case '\u30AA':
                return put(out, o, max, 'o');
            case '\u30AB':
                return put2(out, o, max, 'k', 'a');
            case '\u30AD':
                return put2(out, o, max, 'k', 'i');
            case '\u30AF':
                return put2(out, o, max, 'k', 'u');
            case '\u30B1':
                return put2(out, o, max, 'k', 'e');
            case '\u30B3':
                return put2(out, o, max, 'k', 'o');
            case '\u30B5':
                return put2(out, o, max, 's', 'a');
            case '\u30B7':
                return put3(out, o, max, 's', 'h', 'i');
            case '\u30B9':
                return put2(out, o, max, 's', 'u');
            case '\u30BB':
                return put2(out, o, max, 's', 'e');
            case '\u30BD':
                return put2(out, o, max, 's', 'o');
            case '\u30BF':
                return put2(out, o, max, 't', 'a');
            case '\u30C1':
                return put3(out, o, max, 'c', 'h', 'i');
            case '\u30C3':
                return put3(out, o, max, 't', 's', 'u');
            case '\u30C4':
                return put3(out, o, max, 't', 's', 'u');
            case '\u30C6':
                return put2(out, o, max, 't', 'e');
            case '\u30C8':
                return put2(out, o, max, 't', 'o');
            case '\u30CA':
                return put2(out, o, max, 'n', 'a');
            case '\u30CB':
                return put2(out, o, max, 'n', 'i');
            case '\u30CC':
                return put2(out, o, max, 'n', 'u');
            case '\u30CD':
                return put2(out, o, max, 'n', 'e');
            case '\u30CE':
                return put2(out, o, max, 'n', 'o');
            case '\u30CF':
                return put2(out, o, max, 'h', 'a');
            case '\u30D2':
                return put2(out, o, max, 'h', 'i');
            case '\u30D5':
                return put2(out, o, max, 'f', 'u');
            case '\u30D8':
                return put2(out, o, max, 'h', 'e');
            case '\u30DB':
                return put2(out, o, max, 'h', 'o');
            case '\u30DE':
                return put2(out, o, max, 'm', 'a');
            case '\u30DF':
                return put2(out, o, max, 'm', 'i');
            case '\u30E0':
                return put2(out, o, max, 'm', 'u');
            case '\u30E1':
                return put2(out, o, max, 'm', 'e');
            case '\u30E2':
                return put2(out, o, max, 'm', 'o');
            case '\u30E4':
                return put2(out, o, max, 'y', 'a');
            case '\u30E6':
                return put2(out, o, max, 'y', 'u');
            case '\u30E8':
                return put2(out, o, max, 'y', 'o');
            case '\u30E9':
                return put2(out, o, max, 'r', 'a');
            case '\u30EA':
                return put2(out, o, max, 'r', 'i');
            case '\u30EB':
                return put2(out, o, max, 'r', 'u');
            case '\u30EC':
                return put2(out, o, max, 'r', 'e');
            case '\u30ED':
                return put2(out, o, max, 'r', 'o');
            case '\u30EF':
                return put2(out, o, max, 'w', 'a');
            case '\u30F2':
                return put(out, o, max, 'o');
            case '\u30F3':
                return put(out, o, max, 'n');
            case '\u30AC':
                return put2(out, o, max, 'g', 'a');
            case '\u30AE':
                return put2(out, o, max, 'g', 'i');
            case '\u30B0':
                return put2(out, o, max, 'g', 'u');
            case '\u30B2':
                return put2(out, o, max, 'g', 'e');
            case '\u30B4':
                return put2(out, o, max, 'g', 'o');
            case '\u30B6':
                return put2(out, o, max, 'z', 'a');
            case '\u30B8':
                return put2(out, o, max, 'j', 'i');
            case '\u30BA':
                return put2(out, o, max, 'z', 'u');
            case '\u30BC':
                return put2(out, o, max, 'z', 'e');
            case '\u30BE':
                return put2(out, o, max, 'z', 'o');
            case '\u30C0':
                return put2(out, o, max, 'd', 'a');
            case '\u30C2':
                return put2(out, o, max, 'j', 'i');
            case '\u30C5':
                return put2(out, o, max, 'z', 'u');
            case '\u30C7':
                return put2(out, o, max, 'd', 'e');
            case '\u30C9':
                return put2(out, o, max, 'd', 'o');
            case '\u30D0':
                return put2(out, o, max, 'b', 'a');
            case '\u30D3':
                return put2(out, o, max, 'b', 'i');
            case '\u30D6':
                return put2(out, o, max, 'b', 'u');
            case '\u30D9':
                return put2(out, o, max, 'b', 'e');
            case '\u30DC':
                return put2(out, o, max, 'b', 'o');
            case '\u30D1':
                return put2(out, o, max, 'p', 'a');
            case '\u30D4':
                return put2(out, o, max, 'p', 'i');
            case '\u30D7':
                return put2(out, o, max, 'p', 'u');
            case '\u30DA':
                return put2(out, o, max, 'p', 'e');
            case '\u30DD':
                return put2(out, o, max, 'p', 'o');
            case '\u30F4':
                return put2(out, o, max, 'v', 'u');
            case '\u30FA':
                return put2(out, o, max, 'w', 'o');
            case '\u30FB':
            case '\u3000':
                return o;
            default:
                return o;
        }
    }

    private static int emitCombo(char k, char small, char[] out, int o, int max) {
        char v = smallVowel(small);
        if (k == '\u30D5' && small == '\u30A3') {
            return put2(out, o, max, 'f', 'i');
        }
        if (v == 0) {
            return emitSingle(k, out, o, max);
        }
        switch (k) {
            case '\u30AD':
                return put3(out, o, max, 'k', 'y', v);
            case '\u30AE':
                return put3(out, o, max, 'g', 'y', v);
            case '\u30B7':
                return put3(out, o, max, 's', 'h', v);
            case '\u30C1':
                return put3(out, o, max, 'c', 'h', v);
            case '\u30CB':
                return put3(out, o, max, 'n', 'y', v);
            case '\u30D2':
                return put3(out, o, max, 'h', 'y', v);
            case '\u30DF':
                return put3(out, o, max, 'm', 'y', v);
            case '\u30EA':
                return put3(out, o, max, 'r', 'y', v);
            case '\u30B8':
                return put2(out, o, max, 'j', v);
            case '\u30C2':
                return put2(out, o, max, 'j', v);
            case '\u30D3':
                return put3(out, o, max, 'b', 'y', v);
            case '\u30D4':
                return put3(out, o, max, 'p', 'y', v);
            case '\u30A4':
                return put2(out, o, max, 'y', v);
            case '\u30A8':
                return put2(out, o, max, 'y', v);
            case '\u30A6':
                return put2(out, o, max, 'w', v);
            case '\u30AA':
                return put2(out, o, max, 'w', v);
            case '\u30C6':
                return put3(out, o, max, 't', 'y', v);
            case '\u30C7':
                return put3(out, o, max, 'd', 'y', v);
            case '\u30D5':
                return put3(out, o, max, 'f', 'y', v);
            default:
                int b = emitSingle(k, out, o, max);
                return emitSingle(small, out, b, max);
        }
    }

    private static char smallVowel(char small) {
        if (small == '\u30E3' || small == '\u30A1') {
            return 'a';
        }
        if (small == '\u30E5' || small == '\u30A5') {
            return 'u';
        }
        if (small == '\u30E7' || small == '\u30A9') {
            return 'o';
        }
        if (small == '\u30A3') {
            return 'i';
        }
        if (small == '\u30A7') {
            return 'e';
        }
        return 0;
    }

    private static int put(char[] out, int o, int max, char a) {
        if (o < max) {
            out[o++] = a;
        }
        return o;
    }

    private static int put(char[] out, int o, int max, char a, char b) {
        if (o < max) {
            out[o++] = a;
        }
        if (o < max) {
            out[o++] = b;
        }
        return o;
    }

    private static int put2(char[] out, int o, int max, char a, char b) {
        return put(out, o, max, a, b);
    }

    private static int put3(char[] out, int o, int max, char a, char b, char c) {
        o = put(out, o, max, a, b);
        return put(out, o, max, c);
    }
}
