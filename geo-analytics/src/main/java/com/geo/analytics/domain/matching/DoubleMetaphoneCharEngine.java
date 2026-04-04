package com.geo.analytics.domain.matching;

final class DoubleMetaphoneCharEngine {

    private static final int MAX_CODE_LEN = 4;

    private DoubleMetaphoneCharEngine() {
    }

    static void encode(char[] value, int valueLength, char[] primary, char[] alternate, int[] lengths) {
        lengths[0] = 0;
        lengths[1] = 0;
        if (valueLength <= 0) {
            return;
        }
        boolean slavoGermanic = isSlavoGermanic(value, valueLength);
        int index = isSilentStart(value, valueLength) ? 1 : 0;
        while (!isComplete(lengths) && index <= valueLength - 1) {
            char ch = value[index];
            switch (ch) {
                case 'A':
                case 'E':
                case 'I':
                case 'O':
                case 'U':
                case 'Y':
                    index = handleAEIOUY(primary, alternate, lengths, index);
                    break;
                case 'B':
                    appendBoth(primary, alternate, lengths, 'P');
                    index = charAt(value, valueLength, index + 1) == 'B' ? index + 2 : index + 1;
                    break;
                case '\u00C7':
                    appendBoth(primary, alternate, lengths, 'S');
                    index++;
                    break;
                case 'C':
                    index = handleC(value, valueLength, primary, alternate, lengths, index);
                    break;
                case 'D':
                    index = handleD(value, valueLength, primary, alternate, lengths, index);
                    break;
                case 'F':
                    appendBoth(primary, alternate, lengths, 'F');
                    index = charAt(value, valueLength, index + 1) == 'F' ? index + 2 : index + 1;
                    break;
                case 'G':
                    index = handleG(value, valueLength, primary, alternate, lengths, index, slavoGermanic);
                    break;
                case 'H':
                    index = handleH(value, valueLength, primary, alternate, lengths, index);
                    break;
                case 'J':
                    index = handleJ(value, valueLength, primary, alternate, lengths, index, slavoGermanic);
                    break;
                case 'K':
                    appendBoth(primary, alternate, lengths, 'K');
                    index = charAt(value, valueLength, index + 1) == 'K' ? index + 2 : index + 1;
                    break;
                case 'L':
                    index = handleL(value, valueLength, primary, alternate, lengths, index);
                    break;
                case 'M':
                    appendBoth(primary, alternate, lengths, 'M');
                    index = conditionM0(value, valueLength, index) ? index + 2 : index + 1;
                    break;
                case 'N':
                    appendBoth(primary, alternate, lengths, 'N');
                    index = charAt(value, valueLength, index + 1) == 'N' ? index + 2 : index + 1;
                    break;
                case '\u00D1':
                    appendBoth(primary, alternate, lengths, 'N');
                    index++;
                    break;
                case 'P':
                    index = handleP(value, valueLength, primary, alternate, lengths, index);
                    break;
                case 'Q':
                    appendBoth(primary, alternate, lengths, 'K');
                    index = charAt(value, valueLength, index + 1) == 'Q' ? index + 2 : index + 1;
                    break;
                case 'R':
                    index = handleR(value, valueLength, primary, alternate, lengths, index, slavoGermanic);
                    break;
                case 'S':
                    index = handleS(value, valueLength, primary, alternate, lengths, index, slavoGermanic);
                    break;
                case 'T':
                    index = handleT(value, valueLength, primary, alternate, lengths, index);
                    break;
                case 'V':
                    appendBoth(primary, alternate, lengths, 'F');
                    index = charAt(value, valueLength, index + 1) == 'V' ? index + 2 : index + 1;
                    break;
                case 'W':
                    index = handleW(value, valueLength, primary, alternate, lengths, index);
                    break;
                case 'X':
                    index = handleX(value, valueLength, primary, alternate, lengths, index);
                    break;
                case 'Z':
                    index = handleZ(value, valueLength, primary, alternate, lengths, index, slavoGermanic);
                    break;
                default:
                    index++;
                    break;
            }
        }
    }

    private static char charAt(char[] value, int valueLength, int index) {
        if (index < 0 || index >= valueLength) {
            return 0;
        }
        return value[index];
    }

    private static boolean isVowel(char ch) {
        return ch == 'A' || ch == 'E' || ch == 'I' || ch == 'O' || ch == 'U' || ch == 'Y';
    }

    private static boolean isComplete(int[] lengths) {
        return lengths[0] >= MAX_CODE_LEN && lengths[1] >= MAX_CODE_LEN;
    }

    private static void appendBoth(char[] primary, char[] alternate, int[] lengths, char value) {
        appendPrimary(primary, lengths, value);
        appendAlternate(alternate, lengths, value);
    }

    private static void appendBoth(char[] primary, char[] alternate, int[] lengths, char primaryValue, char alternateValue) {
        appendPrimary(primary, lengths, primaryValue);
        appendAlternate(alternate, lengths, alternateValue);
    }

    private static void appendPrimary(char[] primary, int[] lengths, char value) {
        if (lengths[0] < MAX_CODE_LEN) {
            primary[lengths[0]++] = value;
        }
    }

    private static void appendAlternate(char[] alternate, int[] lengths, char value) {
        if (lengths[1] < MAX_CODE_LEN) {
            alternate[lengths[1]++] = value;
        }
    }

    private static void appendPrimaryPair(char[] primary, int[] lengths, char c0, char c1) {
        appendPrimary(primary, lengths, c0);
        appendPrimary(primary, lengths, c1);
    }

    private static void appendAlternatePair(char[] alternate, int[] lengths, char c0, char c1) {
        appendAlternate(alternate, lengths, c0);
        appendAlternate(alternate, lengths, c1);
    }

    private static void appendBothPair(char[] primary, char[] alternate, int[] lengths, char p0, char p1, char a0, char a1) {
        appendPrimaryPair(primary, lengths, p0, p1);
        appendAlternatePair(alternate, lengths, a0, a1);
    }

    private static void appendBothPairSame(char[] primary, char[] alternate, int[] lengths, char c0, char c1) {
        appendBothPair(primary, alternate, lengths, c0, c1, c0, c1);
    }

    private static boolean regionMatch(char[] value, int valueLength, int start, String pattern) {
        int pl = pattern.length();
        if (start < 0 || start + pl > valueLength) {
            return false;
        }
        for (int i = 0; i < pl; i++) {
            if (value[start + i] != pattern.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isSilentStart(char[] value, int valueLength) {
        return regionMatch(value, valueLength, 0, "GN")
                || regionMatch(value, valueLength, 0, "KN")
                || regionMatch(value, valueLength, 0, "PN")
                || regionMatch(value, valueLength, 0, "WR")
                || regionMatch(value, valueLength, 0, "PS");
    }

    private static boolean isSlavoGermanic(char[] value, int valueLength) {
        for (int i = 0; i < valueLength; i++) {
            char c = value[i];
            if (c == 'W' || c == 'K') {
                return true;
            }
        }
        for (int i = 0; i + 1 < valueLength; i++) {
            if (value[i] == 'C' && value[i + 1] == 'Z') {
                return true;
            }
        }
        return regionMatch(value, valueLength, 0, "WITZ");
    }

    private static int handleAEIOUY(char[] primary, char[] alternate, int[] lengths, int index) {
        if (index == 0) {
            appendBoth(primary, alternate, lengths, 'A');
        }
        return index + 1;
    }

    private static boolean conditionC0(char[] value, int valueLength, int index) {
        if (regionMatch(value, valueLength, index, "CHIA")) {
            return true;
        }
        if (index <= 1) {
            return false;
        }
        if (isVowel(charAt(value, valueLength, index - 2))) {
            return false;
        }
        if (!regionMatch(value, valueLength, index - 1, "ACH")) {
            return false;
        }
        char c = charAt(value, valueLength, index + 2);
        if (c != 'I' && c != 'E') {
            return true;
        }
        return regionMatch(value, valueLength, index - 2, "BACHER") || regionMatch(value, valueLength, index - 2, "MACHER");
    }

    private static boolean conditionCH0(char[] value, int valueLength, int index) {
        if (index != 0) {
            return false;
        }
        if (!regionMatch(value, valueLength, index + 1, "HARAC")
                && !regionMatch(value, valueLength, index + 1, "HARIS")
                && !regionMatch(value, valueLength, index + 1, "HOR")
                && !regionMatch(value, valueLength, index + 1, "HYM")
                && !regionMatch(value, valueLength, index + 1, "HIA")
                && !regionMatch(value, valueLength, index + 1, "HEM")) {
            return false;
        }
        return !regionMatch(value, valueLength, 0, "CHORE");
    }

    private static boolean conditionCH1(char[] value, int valueLength, int index) {
        if (regionMatch(value, valueLength, 0, "VAN ") || regionMatch(value, valueLength, 0, "VON ") || regionMatch(value, valueLength, 0, "SCH")) {
            return true;
        }
        if (regionMatch(value, valueLength, index - 2, "ORCHES")
                || regionMatch(value, valueLength, index - 2, "ARCHIT")
                || regionMatch(value, valueLength, index - 2, "ORCHID")) {
            return true;
        }
        if (charAt(value, valueLength, index + 2) == 'T' || charAt(value, valueLength, index + 2) == 'S') {
            return true;
        }
        return (charAt(value, valueLength, index - 1) == 'A'
                || charAt(value, valueLength, index - 1) == 'O'
                || charAt(value, valueLength, index - 1) == 'U'
                || charAt(value, valueLength, index - 1) == 'E'
                || index == 0)
                && (charAt(value, valueLength, index + 2) == 'L'
                || charAt(value, valueLength, index + 2) == 'R'
                || charAt(value, valueLength, index + 2) == 'N'
                || charAt(value, valueLength, index + 2) == 'M'
                || charAt(value, valueLength, index + 2) == 'B'
                || charAt(value, valueLength, index + 2) == 'H'
                || charAt(value, valueLength, index + 2) == 'F'
                || charAt(value, valueLength, index + 2) == 'V'
                || charAt(value, valueLength, index + 2) == 'W'
                || charAt(value, valueLength, index + 2) == ' '
                || index + 1 == valueLength - 1);
    }

    private static boolean conditionL0(char[] value, int valueLength, int index) {
        if (index == valueLength - 3 && regionMatch(value, valueLength, index - 1, "ILLO")) {
            return true;
        }
        if (index == valueLength - 3 && regionMatch(value, valueLength, index - 1, "ILLA")) {
            return true;
        }
        if (index == valueLength - 3 && regionMatch(value, valueLength, index - 1, "ALLE")) {
            return true;
        }
        if ((regionMatch(value, valueLength, valueLength - 2, "AS") || regionMatch(value, valueLength, valueLength - 2, "OS")
                || charAt(value, valueLength, valueLength - 1) == 'A' || charAt(value, valueLength, valueLength - 1) == 'O')
                && regionMatch(value, valueLength, index - 1, "ALLE")) {
            return true;
        }
        return false;
    }

    private static boolean conditionM0(char[] value, int valueLength, int index) {
        if (charAt(value, valueLength, index + 1) == 'M') {
            return true;
        }
        return regionMatch(value, valueLength, index - 1, "UMB")
                && (index + 1 == valueLength - 1 || regionMatch(value, valueLength, index + 2, "ER"));
    }

    private static int handleC(char[] value, int valueLength, char[] primary, char[] alternate, int[] lengths, int index) {
        if (conditionC0(value, valueLength, index)) {
            appendBoth(primary, alternate, lengths, 'K');
            return index + 2;
        }
        if (index == 0 && regionMatch(value, valueLength, index, "CAESAR")) {
            appendBoth(primary, alternate, lengths, 'S');
            return index + 2;
        }
        if (regionMatch(value, valueLength, index, "CH")) {
            return handleCH(value, valueLength, primary, alternate, lengths, index);
        }
        if (regionMatch(value, valueLength, index, "CZ") && !regionMatch(value, valueLength, index - 2, "WICZ")) {
            appendBoth(primary, alternate, lengths, 'S', 'X');
            return index + 2;
        }
        if (regionMatch(value, valueLength, index + 1, "CIA")) {
            appendBoth(primary, alternate, lengths, 'X');
            return index + 3;
        }
        if (regionMatch(value, valueLength, index, "CC") && !(index == 1 && charAt(value, valueLength, 0) == 'M')) {
            return handleCC(value, valueLength, primary, alternate, lengths, index);
        }
        if (regionMatch(value, valueLength, index, "CK") || regionMatch(value, valueLength, index, "CG") || regionMatch(value, valueLength, index, "CQ")) {
            appendBoth(primary, alternate, lengths, 'K');
            return index + 2;
        }
        if (regionMatch(value, valueLength, index, "CI") || regionMatch(value, valueLength, index, "CE") || regionMatch(value, valueLength, index, "CY")) {
            if (regionMatch(value, valueLength, index, "CIO") || regionMatch(value, valueLength, index, "CIE") || regionMatch(value, valueLength, index, "CIA")) {
                appendBoth(primary, alternate, lengths, 'S', 'X');
            } else {
                appendBoth(primary, alternate, lengths, 'S');
            }
            return index + 2;
        }
        appendBoth(primary, alternate, lengths, 'K');
        if (regionMatch(value, valueLength, index + 1, " C") || regionMatch(value, valueLength, index + 1, " Q") || regionMatch(value, valueLength, index + 1, " G")) {
            return index + 3;
        }
        if ((charAt(value, valueLength, index + 1) == 'C' || charAt(value, valueLength, index + 1) == 'K' || charAt(value, valueLength, index + 1) == 'Q')
                && !regionMatch(value, valueLength, index + 1, "CE") && !regionMatch(value, valueLength, index + 1, "CI")) {
            return index + 2;
        }
        return index + 1;
    }

    private static int handleCC(char[] value, int valueLength, char[] primary, char[] alternate, int[] lengths, int index) {
        char n2 = charAt(value, valueLength, index + 2);
        if ((n2 == 'I' || n2 == 'E' || n2 == 'H') && !regionMatch(value, valueLength, index + 2, "HU")) {
            if (index == 1 && charAt(value, valueLength, index - 1) == 'A'
                    || regionMatch(value, valueLength, index - 1, "UCCEE")
                    || regionMatch(value, valueLength, index - 1, "UCCES")) {
                appendBothPairSame(primary, alternate, lengths, 'K', 'S');
            } else {
                appendBoth(primary, alternate, lengths, 'X');
            }
            return index + 3;
        }
        appendBoth(primary, alternate, lengths, 'K');
        return index + 2;
    }

    private static int handleCH(char[] value, int valueLength, char[] primary, char[] alternate, int[] lengths, int index) {
        if (index > 0 && regionMatch(value, valueLength, index, "CHAE")) {
            appendBoth(primary, alternate, lengths, 'K', 'X');
            return index + 2;
        }
        if (conditionCH0(value, valueLength, index)) {
            appendBoth(primary, alternate, lengths, 'K');
            return index + 2;
        }
        if (conditionCH1(value, valueLength, index)) {
            appendBoth(primary, alternate, lengths, 'K');
            return index + 2;
        }
        if (index > 0) {
            if (regionMatch(value, valueLength, 0, "MC")) {
                appendBoth(primary, alternate, lengths, 'K');
            } else {
                appendBoth(primary, alternate, lengths, 'X', 'K');
            }
        } else {
            appendBoth(primary, alternate, lengths, 'X');
        }
        return index + 2;
    }

    private static int handleD(char[] value, int valueLength, char[] primary, char[] alternate, int[] lengths, int index) {
        if (regionMatch(value, valueLength, index, "DG")) {
            char nx = charAt(value, valueLength, index + 2);
            if (nx == 'I' || nx == 'E' || nx == 'Y') {
                appendBoth(primary, alternate, lengths, 'J');
                return index + 3;
            }
            appendBothPairSame(primary, alternate, lengths, 'T', 'K');
            return index + 2;
        }
        if (regionMatch(value, valueLength, index, "DT") || regionMatch(value, valueLength, index, "DD")) {
            appendBoth(primary, alternate, lengths, 'T');
            return index + 2;
        }
        appendBoth(primary, alternate, lengths, 'T');
        return index + 1;
    }

    private static int handleGH(char[] value, int valueLength, char[] primary, char[] alternate, int[] lengths, int index) {
        if (index > 0 && !isVowel(charAt(value, valueLength, index - 1))) {
            appendBoth(primary, alternate, lengths, 'K');
            return index + 2;
        }
        if (index == 0) {
            if (charAt(value, valueLength, index + 2) == 'I') {
                appendBoth(primary, alternate, lengths, 'J');
            } else {
                appendBoth(primary, alternate, lengths, 'K');
            }
            return index + 2;
        }
        if (index > 1 && (charAt(value, valueLength, index - 2) == 'B'
                || charAt(value, valueLength, index - 2) == 'H'
                || charAt(value, valueLength, index - 2) == 'D')
                || index > 2 && (charAt(value, valueLength, index - 3) == 'B'
                || charAt(value, valueLength, index - 3) == 'H'
                || charAt(value, valueLength, index - 3) == 'D')
                || index > 3 && (charAt(value, valueLength, index - 4) == 'B'
                || charAt(value, valueLength, index - 4) == 'H')) {
            return index + 2;
        }
        if (index > 2 && charAt(value, valueLength, index - 1) == 'U') {
            char z = charAt(value, valueLength, index - 3);
            if (z == 'C' || z == 'G' || z == 'L' || z == 'R' || z == 'T') {
                appendBoth(primary, alternate, lengths, 'F');
            }
        } else if (index > 0 && charAt(value, valueLength, index - 1) != 'I') {
            appendBoth(primary, alternate, lengths, 'K');
        }
        return index + 2;
    }

    private static int handleG(char[] value, int valueLength, char[] primary, char[] alternate, int[] lengths, int index, boolean slavoGermanic) {
        if (charAt(value, valueLength, index + 1) == 'H') {
            return handleGH(value, valueLength, primary, alternate, lengths, index);
        }
        if (charAt(value, valueLength, index + 1) == 'N') {
            if (index == 1 && isVowel(charAt(value, valueLength, 0)) && !slavoGermanic) {
                appendPrimaryPair(primary, lengths, 'K', 'N');
                appendAlternate(alternate, lengths, 'N');
            } else if (!regionMatch(value, valueLength, index + 2, "EY")
                    && charAt(value, valueLength, index + 1) != 'Y' && !slavoGermanic) {
                appendPrimary(primary, lengths, 'N');
                appendAlternatePair(alternate, lengths, 'K', 'N');
            } else {
                appendBothPairSame(primary, alternate, lengths, 'K', 'N');
            }
            return index + 2;
        }
        if (regionMatch(value, valueLength, index + 1, "LI") && !slavoGermanic) {
            appendPrimaryPair(primary, lengths, 'K', 'L');
            appendAlternate(alternate, lengths, 'L');
            return index + 2;
        }
        if (index == 0 && (charAt(value, valueLength, index + 1) == 'Y'
                || regionMatch(value, valueLength, index + 1, "ES")
                || regionMatch(value, valueLength, index + 1, "EP")
                || regionMatch(value, valueLength, index + 1, "EB")
                || regionMatch(value, valueLength, index + 1, "EL")
                || regionMatch(value, valueLength, index + 1, "EY")
                || regionMatch(value, valueLength, index + 1, "IB")
                || regionMatch(value, valueLength, index + 1, "IL")
                || regionMatch(value, valueLength, index + 1, "IN")
                || regionMatch(value, valueLength, index + 1, "IE")
                || regionMatch(value, valueLength, index + 1, "EI")
                || regionMatch(value, valueLength, index + 1, "ER"))) {
            appendBoth(primary, alternate, lengths, 'K', 'J');
            return index + 2;
        }
        if ((regionMatch(value, valueLength, index + 1, "ER") || charAt(value, valueLength, index + 1) == 'Y')
                && !regionMatch(value, valueLength, 0, "DANGER")
                && !regionMatch(value, valueLength, 0, "RANGER")
                && !regionMatch(value, valueLength, 0, "MANGER")
                && charAt(value, valueLength, index - 1) != 'E'
                && charAt(value, valueLength, index - 1) != 'I'
                && !regionMatch(value, valueLength, index - 1, "RGY")
                && !regionMatch(value, valueLength, index - 1, "OGY")) {
            appendBoth(primary, alternate, lengths, 'K', 'J');
            return index + 2;
        }
        if (charAt(value, valueLength, index + 1) == 'E'
                || charAt(value, valueLength, index + 1) == 'I'
                || charAt(value, valueLength, index + 1) == 'Y'
                || regionMatch(value, valueLength, index - 1, "AGGI")
                || regionMatch(value, valueLength, index - 1, "OGGI")) {
            if (regionMatch(value, valueLength, 0, "VAN ") || regionMatch(value, valueLength, 0, "VON ") || regionMatch(value, valueLength, 0, "SCH")
                    || regionMatch(value, valueLength, index + 1, "ET")) {
                appendBoth(primary, alternate, lengths, 'K');
            } else if (regionMatch(value, valueLength, index + 1, "IER")) {
                appendBoth(primary, alternate, lengths, 'J');
            } else {
                appendBoth(primary, alternate, lengths, 'J', 'K');
            }
            return index + 2;
        }
        if (charAt(value, valueLength, index + 1) == 'G') {
            index += 2;
        } else {
            index++;
        }
        appendBoth(primary, alternate, lengths, 'K');
        return index;
    }

    private static int handleH(char[] value, int valueLength, char[] primary, char[] alternate, int[] lengths, int index) {
        if ((index == 0 || isVowel(charAt(value, valueLength, index - 1))) && isVowel(charAt(value, valueLength, index + 1))) {
            appendBoth(primary, alternate, lengths, 'H');
            return index + 2;
        }
        return index + 1;
    }

    private static int handleJ(char[] value, int valueLength, char[] primary, char[] alternate, int[] lengths, int index, boolean slavoGermanic) {
        if (regionMatch(value, valueLength, index, "JOSE") || regionMatch(value, valueLength, 0, "SAN ")) {
            if (index == 0 && charAt(value, valueLength, index + 4) == ' ' || valueLength == 4 || regionMatch(value, valueLength, 0, "SAN ")) {
                appendBoth(primary, alternate, lengths, 'H');
            } else {
                appendBoth(primary, alternate, lengths, 'J', 'H');
            }
            index++;
            return index;
        }
        if (index == 0 && !regionMatch(value, valueLength, index, "JOSE")) {
            appendBoth(primary, alternate, lengths, 'J', 'A');
        } else if (isVowel(charAt(value, valueLength, index - 1)) && !slavoGermanic
                && (charAt(value, valueLength, index + 1) == 'A' || charAt(value, valueLength, index + 1) == 'O')) {
            appendBoth(primary, alternate, lengths, 'J', 'H');
        } else if (index == valueLength - 1) {
            appendBoth(primary, alternate, lengths, 'J', ' ');
        } else if (charAt(value, valueLength, index + 1) != 'L'
                && charAt(value, valueLength, index + 1) != 'T'
                && charAt(value, valueLength, index + 1) != 'K'
                && charAt(value, valueLength, index + 1) != 'S'
                && charAt(value, valueLength, index + 1) != 'N'
                && charAt(value, valueLength, index + 1) != 'M'
                && charAt(value, valueLength, index + 1) != 'B'
                && charAt(value, valueLength, index + 1) != 'Z'
                && charAt(value, valueLength, index - 1) != 'S'
                && charAt(value, valueLength, index - 1) != 'K'
                && charAt(value, valueLength, index - 1) != 'L') {
            appendBoth(primary, alternate, lengths, 'J');
        }
        if (charAt(value, valueLength, index + 1) == 'J') {
            return index + 2;
        }
        return index + 1;
    }

    private static int handleL(char[] value, int valueLength, char[] primary, char[] alternate, int[] lengths, int index) {
        if (charAt(value, valueLength, index + 1) == 'L') {
            if (conditionL0(value, valueLength, index)) {
                appendPrimary(primary, lengths, 'L');
            } else {
                appendBoth(primary, alternate, lengths, 'L');
            }
            return index + 2;
        }
        appendBoth(primary, alternate, lengths, 'L');
        return index + 1;
    }

    private static int handleP(char[] value, int valueLength, char[] primary, char[] alternate, int[] lengths, int index) {
        if (charAt(value, valueLength, index + 1) == 'H') {
            appendBoth(primary, alternate, lengths, 'F');
            return index + 2;
        }
        appendBoth(primary, alternate, lengths, 'P');
        char nx = charAt(value, valueLength, index + 1);
        if (nx == 'P' || nx == 'B') {
            return index + 2;
        }
        return index + 1;
    }

    private static int handleR(char[] value, int valueLength, char[] primary, char[] alternate, int[] lengths, int index, boolean slavoGermanic) {
        if (index == valueLength - 1 && !slavoGermanic && regionMatch(value, valueLength, index - 2, "IE")
                && !regionMatch(value, valueLength, index - 4, "ME")
                && !regionMatch(value, valueLength, index - 4, "MA")) {
            appendAlternate(alternate, lengths, 'R');
        } else {
            appendBoth(primary, alternate, lengths, 'R');
        }
        return charAt(value, valueLength, index + 1) == 'R' ? index + 2 : index + 1;
    }

    private static int handleSC(char[] value, int valueLength, char[] primary, char[] alternate, int[] lengths, int index) {
        if (charAt(value, valueLength, index + 2) == 'H') {
            if (regionMatch(value, valueLength, index + 3, "OO")
                    || regionMatch(value, valueLength, index + 3, "ER")
                    || regionMatch(value, valueLength, index + 3, "EN")
                    || regionMatch(value, valueLength, index + 3, "UY")
                    || regionMatch(value, valueLength, index + 3, "ED")
                    || regionMatch(value, valueLength, index + 3, "EM")) {
                if (regionMatch(value, valueLength, index + 3, "ER") || regionMatch(value, valueLength, index + 3, "EN")) {
                    appendPrimary(primary, lengths, 'X');
                    appendAlternatePair(alternate, lengths, 'S', 'K');
                } else {
                    appendBothPairSame(primary, alternate, lengths, 'S', 'K');
                }
            } else if (index == 0 && !isVowel(charAt(value, valueLength, 3)) && charAt(value, valueLength, 3) != 'W') {
                appendBoth(primary, alternate, lengths, 'X', 'S');
            } else {
                appendBoth(primary, alternate, lengths, 'X');
            }
        } else if (charAt(value, valueLength, index + 2) == 'I'
                || charAt(value, valueLength, index + 2) == 'E'
                || charAt(value, valueLength, index + 2) == 'Y') {
            appendBoth(primary, alternate, lengths, 'S');
        } else {
            appendBothPairSame(primary, alternate, lengths, 'S', 'K');
        }
        return index + 3;
    }

    private static int handleS(char[] value, int valueLength, char[] primary, char[] alternate, int[] lengths, int index, boolean slavoGermanic) {
        if (regionMatch(value, valueLength, index - 1, "ISL") || regionMatch(value, valueLength, index - 1, "YSL")) {
            return index + 1;
        }
        if (index == 0 && regionMatch(value, valueLength, index, "SUGAR")) {
            appendBoth(primary, alternate, lengths, 'X', 'S');
            return index + 1;
        }
        if (regionMatch(value, valueLength, index, "SH")) {
            if (regionMatch(value, valueLength, index + 1, "HEIM")
                    || regionMatch(value, valueLength, index + 1, "HOEK")
                    || regionMatch(value, valueLength, index + 1, "HOLM")
                    || regionMatch(value, valueLength, index + 1, "HOLZ")) {
                appendBoth(primary, alternate, lengths, 'S');
            } else {
                appendBoth(primary, alternate, lengths, 'X');
            }
            return index + 2;
        }
        if (regionMatch(value, valueLength, index, "SIO") || regionMatch(value, valueLength, index, "SIA") || regionMatch(value, valueLength, index, "SIAN")) {
            if (slavoGermanic) {
                appendBoth(primary, alternate, lengths, 'S');
            } else {
                appendBoth(primary, alternate, lengths, 'S', 'X');
            }
            return index + 3;
        }
        if (index == 0 && (charAt(value, valueLength, index + 1) == 'M'
                || charAt(value, valueLength, index + 1) == 'N'
                || charAt(value, valueLength, index + 1) == 'L'
                || charAt(value, valueLength, index + 1) == 'W')
                || charAt(value, valueLength, index + 1) == 'Z') {
            appendBoth(primary, alternate, lengths, 'S', 'X');
            return charAt(value, valueLength, index + 1) == 'Z' ? index + 2 : index + 1;
        }
        if (regionMatch(value, valueLength, index, "SC")) {
            return handleSC(value, valueLength, primary, alternate, lengths, index);
        }
        if (index == valueLength - 1 && (regionMatch(value, valueLength, index - 2, "AI")
                || regionMatch(value, valueLength, index - 2, "OI"))) {
            appendAlternate(alternate, lengths, 'S');
        } else {
            appendBoth(primary, alternate, lengths, 'S');
        }
        char nx = charAt(value, valueLength, index + 1);
        if (nx == 'S' || nx == 'Z') {
            return index + 2;
        }
        return index + 1;
    }

    private static int handleT(char[] value, int valueLength, char[] primary, char[] alternate, int[] lengths, int index) {
        if (regionMatch(value, valueLength, index, "TION") || regionMatch(value, valueLength, index, "TIA") || regionMatch(value, valueLength, index, "TCH")) {
            appendBoth(primary, alternate, lengths, 'X');
            return index + 3;
        }
        if (regionMatch(value, valueLength, index, "TH") || regionMatch(value, valueLength, index, "TTH")) {
            if (regionMatch(value, valueLength, index + 2, "OM")
                    || regionMatch(value, valueLength, index + 2, "AM")
                    || regionMatch(value, valueLength, 0, "VAN ")
                    || regionMatch(value, valueLength, 0, "VON ")
                    || regionMatch(value, valueLength, 0, "SCH")) {
                appendBoth(primary, alternate, lengths, 'T');
            } else {
                appendBoth(primary, alternate, lengths, '0', 'T');
            }
            return index + 2;
        }
        appendBoth(primary, alternate, lengths, 'T');
        char nx = charAt(value, valueLength, index + 1);
        if (nx == 'T' || nx == 'D') {
            return index + 2;
        }
        return index + 1;
    }

    private static int handleW(char[] value, int valueLength, char[] primary, char[] alternate, int[] lengths, int index) {
        if (regionMatch(value, valueLength, index, "WR")) {
            appendBoth(primary, alternate, lengths, 'R');
            return index + 2;
        }
        if (index == 0 && (isVowel(charAt(value, valueLength, index + 1)) || regionMatch(value, valueLength, index, "WH"))) {
            if (isVowel(charAt(value, valueLength, index + 1))) {
                appendBoth(primary, alternate, lengths, 'A', 'F');
            } else {
                appendBoth(primary, alternate, lengths, 'A');
            }
            return index + 1;
        }
        if (index == valueLength - 1 && isVowel(charAt(value, valueLength, index - 1))
                || regionMatch(value, valueLength, index - 1, "EWSKI")
                || regionMatch(value, valueLength, index - 1, "EWSKY")
                || regionMatch(value, valueLength, index - 1, "OWSKI")
                || regionMatch(value, valueLength, index - 1, "OWSKY")
                || regionMatch(value, valueLength, 0, "SCH")) {
            appendAlternate(alternate, lengths, 'F');
            return index + 1;
        }
        if (regionMatch(value, valueLength, index, "WICZ") || regionMatch(value, valueLength, index, "WITZ")) {
            appendPrimaryPair(primary, lengths, 'T', 'S');
            appendAlternatePair(alternate, lengths, 'F', 'X');
            return index + 4;
        }
        return index + 1;
    }

    private static int handleX(char[] value, int valueLength, char[] primary, char[] alternate, int[] lengths, int index) {
        if (index == 0) {
            appendBoth(primary, alternate, lengths, 'S');
            index++;
        } else {
            if (!(index == valueLength - 1 && (regionMatch(value, valueLength, index - 3, "IAU")
                    || regionMatch(value, valueLength, index - 3, "EAU")
                    || regionMatch(value, valueLength, index - 2, "AU")
                    || regionMatch(value, valueLength, index - 2, "OU")))) {
                appendBothPairSame(primary, alternate, lengths, 'K', 'S');
            }
            char nx = charAt(value, valueLength, index + 1);
            if (nx == 'C' || nx == 'X') {
                index += 2;
            } else {
                index++;
            }
        }
        return index;
    }

    private static int handleZ(char[] value, int valueLength, char[] primary, char[] alternate, int[] lengths, int index, boolean slavoGermanic) {
        if (charAt(value, valueLength, index + 1) == 'H') {
            appendBoth(primary, alternate, lengths, 'J');
            return index + 2;
        }
        if (regionMatch(value, valueLength, index + 1, "ZO")
                || regionMatch(value, valueLength, index + 1, "ZI")
                || regionMatch(value, valueLength, index + 1, "ZA")
                || slavoGermanic && index > 0 && charAt(value, valueLength, index - 1) != 'T') {
            appendPrimary(primary, lengths, 'S');
            appendAlternatePair(alternate, lengths, 'T', 'S');
        } else {
            appendBoth(primary, alternate, lengths, 'S');
        }
        return charAt(value, valueLength, index + 1) == 'Z' ? index + 2 : index + 1;
    }
}
