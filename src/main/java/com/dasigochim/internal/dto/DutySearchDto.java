package com.dasigochim.internal.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * C-1 {@code POST /internal/v1/rag/duty-search} 계약.
 *
 * <p>AI 서버가 snake_case 로 보내고 snake_case 로 받는다
 * (ai-server/app/backends/base.py 의 duty_search).
 */
public final class DutySearchDto {
    private DutySearchDto() {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Request(
            String sessionId,
            @NotBlank String queryText,
            Integer topK) {

        /** AI 서버 기본값과 같게 맞춘다. 안 보내오면 8건. */
        public int topKOrDefault() {
            return topK == null || topK <= 0 ? 8 : topK;
        }
    }

    /** 후보 1건 = 부서가 아니라 업무범위 문장 1개다. 같은 부서가 여러 번 나올 수 있다. */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Candidate(String code, String name, String dutyText, double score) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Response(List<Candidate> candidates, int corpusVersion) {}
}
