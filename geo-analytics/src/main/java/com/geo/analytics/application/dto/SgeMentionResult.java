package com.geo.analytics.application.dto;

public record SgeMentionResult(boolean mentioned, int mentionCount, String rawResponseJson) {}
