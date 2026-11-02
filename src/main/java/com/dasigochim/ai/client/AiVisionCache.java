package com.dasigochim.ai.client;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 신고 1건의 사진 판독 결과를 잠깐 들고 있는다.
 *
 * <p>질문 생성과 초안 생성이 같은 판독 결과를 쓰는데, 매번 다시 부르면 Gemini 비전
 * 호출이 두 번 나가고 두 결과가 서로 어긋날 수 있다. 신고 플로우는 몇 분 안에 끝나므로
 * 프로세스 메모리에 TTL 로만 담는다 — 재시작하면 다시 판독할 뿐이라 잃어도 무해하다.
 */
@Component
@ConditionalOnProperty(name = "app.external.ai", havingValue = "http")
public class AiVisionCache {
    private static final Duration TTL = Duration.ofMinutes(30);
    private static final int MAX_ENTRIES = 500;

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    public AiServerClient.VisionResponse get(String sessionId) {
        Entry entry = entries.get(sessionId);
        if (entry == null) return null;
        if (entry.isExpired()) {
            entries.remove(sessionId);
            return null;
        }
        return entry.vision();
    }

    public void put(String sessionId, AiServerClient.VisionResponse vision) {
        if (entries.size() >= MAX_ENTRIES) {
            entries.values().removeIf(Entry::isExpired);
            if (entries.size() >= MAX_ENTRIES) entries.clear();
        }
        entries.put(sessionId, new Entry(vision, Instant.now().plus(TTL)));
    }

    private record Entry(AiServerClient.VisionResponse vision, Instant expiresAt) {
        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
}
