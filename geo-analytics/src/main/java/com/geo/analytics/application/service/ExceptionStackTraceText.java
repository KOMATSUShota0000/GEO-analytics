package com.geo.analytics.application.service;

import java.io.PrintWriter;
import java.io.StringWriter;

public final class ExceptionStackTraceText {
    private static final int MAX_LENGTH = 20000;

    private ExceptionStackTraceText() {
    }

    public static String of(Throwable throwable) {
        if (throwable == null) {
            return "";
        }
        StringWriter stringWriter = new StringWriter();
        throwable.printStackTrace(new PrintWriter(stringWriter));
        String full = stringWriter.toString();
        if (full.length() <= MAX_LENGTH) {
            return full;
        }
        return full.substring(0, MAX_LENGTH);
    }
}
