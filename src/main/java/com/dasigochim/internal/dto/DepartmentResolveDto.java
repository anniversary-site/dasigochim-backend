package com.dasigochim.internal.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * C-2 {@code POST /internal/v1/departments/resolve} 계약.
 *
 * <p>LLM 이 지어낸 부서 코드를 그대로 믿지 않기 위한 관문이다. 결정론적 판단이라
 * 부서 테이블을 쥔 백엔드 몫이다.
 */
public final class DepartmentResolveDto {
    private DepartmentResolveDto() {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Request(String sessionId, String departmentCode, Double confidence, String rationale) {

        public double confidenceOrZero() {
            return confidence == null ? 0.0 : confidence;
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record DepartmentPayload(String code, String name, String contact) {}

    /**
     * {@code confidence} 는 fallback 일 때 <b>null</b> 이다. AI 서버가 이걸 -1 로 바꿔 응답한다
     * (Dify 시절 number 출력이 null 을 못 담아 생긴 규칙).
     */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Response(DepartmentPayload department, String assignedBy, Double confidence, String rationale) {

        public static Response agent(DepartmentPayload department, double confidence, String rationale) {
            return new Response(department, "agent", confidence, rationale);
        }

        public static Response fallback(DepartmentPayload department, String rationale) {
            return new Response(department, "fallback", null, rationale);
        }
    }
}
