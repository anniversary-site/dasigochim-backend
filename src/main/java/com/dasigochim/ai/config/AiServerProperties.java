package com.dasigochim.ai.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ai-server(FastAPI) 연동 설정.
 *
 * <p>사진 판독·질문 생성·초안 생성은 모두 Gemini 호출이라 응답이 느리다.
 * 질문 3개를 순차 생성하는 호출은 특히 오래 걸려 read timeout 을 넉넉히 잡는다.
 */
@ConfigurationProperties(prefix = "app.external.ai-server")
public record AiServerProperties(String baseUrl, Duration connectTimeout, Duration readTimeout, int questionCount) {
    public AiServerProperties {
        if (baseUrl == null || baseUrl.isBlank()) baseUrl = "http://localhost:8000";
        if (connectTimeout == null) connectTimeout = Duration.ofSeconds(5);
        if (readTimeout == null) readTimeout = Duration.ofSeconds(120);
        if (questionCount <= 0) questionCount = 3;
    }
}
