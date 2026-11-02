package com.dasigochim.insight.domain;

import java.util.Locale;

/** ⑤ 인사이트가 권고하는 처리 우선순위. AI 서버는 소문자로 준다. */
public enum InsightPriority {
    IMMEDIATE, HIGH, NORMAL, LOW;

    public static InsightPriority from(String value) {
        if (value == null || value.isBlank()) return NORMAL;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return NORMAL;
        }
    }
}
