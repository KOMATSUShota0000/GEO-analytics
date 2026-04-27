package com.geo.analytics.infrastructure.crawler.extraction;

public final class HtmlTagStripper {
    private static final int S_TEXT = 0;
    private static final int S_TAG = 1;
    private static final int S_INV_S = 2;
    private static final int S_INV_T = 3;
    private static final int T0 = 0;
    private static final int T1 = 1;
    private static final int T2 = 2;
    private int state;
    private int tphase;
    private final char[] tbuf;
    private int tlen;
    private int tkind;
    private int ttail;
    private int ms;
    private int mt;
    public HtmlTagStripper() {
        tbuf = new char[64];
    }
    public void reset() {
        state = S_TEXT;
        tphase = 0;
        tlen = 0;
        tkind = 0;
        ttail = 0;
        ms = 0;
        mt = 0;
    }
    public void processCodePoint(int cp, StringBuilder out) {
        if (state == S_TEXT) {
            if (cp == 60) {
                state = S_TAG;
                tphase = T0;
                tlen = 0;
                tkind = 0;
                ttail = 0;
            } else {
                out.appendCodePoint(cp);
            }
            return;
        }
        if (state == S_TAG) {
            if (cp < 0x10000) {
                tag((char) cp);
            } else {
                tphase = T2;
                tkind = 0;
                ttail = 0;
            }
            return;
        }
        if (state == S_INV_S) {
            invS(cp);
            return;
        }
        invT(cp);
    }
    private void invS(int cp) {
        if (cp < 0x10000) {
            invSChar((char) cp);
        } else {
            ms = 0;
        }
    }
    private void invSChar(char c) {
        if (ms == 0) {
            if (c == '<') {
                ms = 1;
            }
            return;
        }
        if (ms == 1) {
            if (c == '/') {
                ms = 2;
            } else {
                ms = 0;
                if (c == '<') {
                    ms = 1;
                }
            }
            return;
        }
        if (ms == 2) {
            if (le(c, 's')) {
                ms = 3;
            } else {
                ms = 0;
                if (c == '<') {
                    ms = 1;
                }
            }
            return;
        }
        if (ms == 3) {
            if (le(c, 'c')) {
                ms = 4;
            } else {
                ms = 0;
                if (c == '<') {
                    ms = 1;
                }
            }
            return;
        }
        if (ms == 4) {
            if (le(c, 'r')) {
                ms = 5;
            } else {
                ms = 0;
                if (c == '<') {
                    ms = 1;
                }
            }
            return;
        }
        if (ms == 5) {
            if (le(c, 'i')) {
                ms = 6;
            } else {
                ms = 0;
                if (c == '<') {
                    ms = 1;
                }
            }
            return;
        }
        if (ms == 6) {
            if (le(c, 'p')) {
                ms = 7;
            } else {
                ms = 0;
                if (c == '<') {
                    ms = 1;
                }
            }
            return;
        }
        if (ms == 7) {
            if (le(c, 't')) {
                ms = 8;
            } else {
                ms = 0;
                if (c == '<') {
                    ms = 1;
                }
            }
            return;
        }
        if (ms == 8) {
            if (Character.isWhitespace(c)) {
                ms = 9;
                return;
            }
            if (c == '>') {
                toText();
                return;
            }
            ms = 0;
            if (c == '<') {
                ms = 1;
            }
            return;
        }
        if (ms == 9) {
            if (c == '>') {
                toText();
                return;
            }
            if (!Character.isWhitespace(c)) {
                ms = 0;
                if (c == '<') {
                    ms = 1;
                }
            }
            return;
        }
    }
    private void invT(int cp) {
        if (cp < 0x10000) {
            invTChar((char) cp);
        } else {
            mt = 0;
        }
    }
    private void invTChar(char c) {
        if (mt == 0) {
            if (c == '<') {
                mt = 1;
            }
            return;
        }
        if (mt == 1) {
            if (c == '/') {
                mt = 2;
            } else {
                mt = 0;
                if (c == '<') {
                    mt = 1;
                }
            }
            return;
        }
        if (mt == 2) {
            if (le(c, 's')) {
                mt = 3;
            } else {
                mt = 0;
                if (c == '<') {
                    mt = 1;
                }
            }
            return;
        }
        if (mt == 3) {
            if (le(c, 't')) {
                mt = 4;
            } else {
                mt = 0;
                if (c == '<') {
                    mt = 1;
                }
            }
            return;
        }
        if (mt == 4) {
            if (le(c, 'y')) {
                mt = 5;
            } else {
                mt = 0;
                if (c == '<') {
                    mt = 1;
                }
            }
            return;
        }
        if (mt == 5) {
            if (le(c, 'l')) {
                mt = 6;
            } else {
                mt = 0;
                if (c == '<') {
                    mt = 1;
                }
            }
            return;
        }
        if (mt == 6) {
            if (le(c, 'e')) {
                mt = 7;
            } else {
                mt = 0;
                if (c == '<') {
                    mt = 1;
                }
            }
            return;
        }
        if (mt == 7) {
            if (Character.isWhitespace(c)) {
                mt = 8;
                return;
            }
            if (c == '>') {
                toText();
                return;
            }
            mt = 0;
            if (c == '<') {
                mt = 1;
            }
            return;
        }
        if (mt == 8) {
            if (c == '>') {
                toText();
                return;
            }
            if (!Character.isWhitespace(c)) {
                mt = 0;
                if (c == '<') {
                    mt = 1;
                }
            }
            return;
        }
    }
    private void toText() {
        state = S_TEXT;
        tphase = 0;
        tlen = 0;
        tkind = 0;
        ttail = 0;
        ms = 0;
        mt = 0;
    }
    private static boolean le(char c, char e) {
        return al(c) == e;
    }
    private static char al(char c) {
        if (c >= 65 && c <= 90) {
            return (char) (c + 32);
        }
        return c;
    }
    private void tag(char c) {
        if (tphase == T0) {
            if (Character.isWhitespace(c)) {
                return;
            }
            if (c == '!' || c == '?') {
                tphase = T2;
                tkind = 0;
                ttail = 0;
            } else if (c == '/') {
                tphase = T2;
                tkind = 0;
                ttail = 0;
            } else if (isName0(c)) {
                tphase = T1;
                tlen = 0;
                tbuf[tlen++] = al(c);
            } else {
                tphase = T2;
                tkind = 0;
            }
            return;
        }
        if (tphase == T1) {
            if (isName1(c) && tlen < 64) {
                tbuf[tlen++] = al(c);
                return;
            }
            tkind = kind();
            tphase = T2;
            ttail = 0;
            if (c == '>') {
                endTag();
                return;
            }
            if (c == '/') {
                ttail = 1;
            }
            tskip(c);
            return;
        }
        if (tphase == T2) {
            tskip(c);
        }
    }
    private void tskip(char c) {
        if (c == '>') {
            if (ttail == 1 || ttail == 2) {
                toText();
            } else {
                endTag();
            }
            return;
        }
        if (c == '/' && ttail == 0) {
            ttail = 1;
        } else if (Character.isWhitespace(c) && ttail == 1) {
            ttail = 2;
        } else if (!Character.isWhitespace(c) && ttail == 1) {
            ttail = 0;
        } else if (!Character.isWhitespace(c) && ttail == 2) {
            ttail = 0;
        }
    }
    private void endTag() {
        if (ttail == 1 || ttail == 2) {
            toText();
        } else if (tkind == 1) {
            state = S_INV_S;
            ms = 0;
        } else if (tkind == 2) {
            state = S_INV_T;
            mt = 0;
        } else {
            toText();
        }
    }
    private int kind() {
        if (tlen == 5 && m("style")) {
            return 2;
        }
        if (tlen == 6 && m("script")) {
            return 1;
        }
        return 0;
    }
    private boolean m(String s) {
        if (tlen != s.length()) {
            return false;
        }
        for (int i = 0; i < tlen; i++) {
            if (tbuf[i] != s.charAt(i)) {
                return false;
            }
        }
        return true;
    }
    private static boolean isName0(char c) {
        return (c >= 97 && c <= 122) || (c >= 65 && c <= 90) || c == 95;
    }
    private static boolean isName1(char c) {
        return isName0(c) || (c >= 48 && c <= 57) || c == 45 || c == 58;
    }
}
