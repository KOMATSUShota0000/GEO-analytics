package com.geo.analytics.application.dto;

import java.util.List;

public record GapLlmResult(String gapReason, List<String> actions) {}
