package com.geo.analytics.infrastructure.ai;

import com.geo.analytics.domain.model.Tone;

public final class TaskTonePrompts {

    private TaskTonePrompts() {}

    public static String userMessage(Tone tone, String title, String currentContent) {
        return "Rewrite the remediation task body (content field only). Keep factual meaning; adjust writing style.\nTarget tone: "
                + tone.name()
                + "\nTask title:\n"
                + title
                + "\n\nCurrent content (Markdown):\n"
                + currentContent
                + "\n\nRespond with JSON only: {\"content\": \"...\"} where content is Markdown.";
    }

    public static final String SYSTEM =
            "You are a GEO remediation editor. Rewrite only the Markdown body text to match the requested tone. "
                    + "Do not change factual claims beyond tone and phrasing. Output valid JSON matching the schema exactly.";
}
