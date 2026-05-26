package com.geo.analytics.web.dto;

import com.geo.analytics.domain.model.Tone;
import jakarta.validation.constraints.NotNull;

public record TaskToneRegenerateRequest(@NotNull Tone tone) {}
