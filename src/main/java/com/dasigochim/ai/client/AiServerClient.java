package com.dasigochim.ai.client;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.dasigochim.ai.config.AiServerProperties;
import com.dasigochim.common.exception.BusinessException;
import com.dasigochim.common.exception.ErrorCode;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * ai-server 의 5개 REST 엔드포인트를 그대로 감싼 저수준 클라이언트.
 *
 * <p>요청·응답 JSON 은 snake_case 이므로 레코드에 {@link JsonNaming} 을 붙여 매핑한다.
 * 도메인 변환은 하지 않는다 — 그건 {@link HttpAiAnalysisClient} 의 일이다.
 */
@Component
@ConditionalOnProperty(name = "app.external.ai", havingValue = "http")
public class AiServerClient {
    private static final Logger log = LoggerFactory.getLogger(AiServerClient.class);

    private final RestClient restClient;
    private final String baseUrl;

    public AiServerClient(AiServerProperties properties) {
        this.baseUrl = properties.baseUrl();
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory(properties.connectTimeout(), properties.readTimeout()))
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
        log.info("AI 서버 연동 활성화: {} (read timeout {}s)", properties.baseUrl(), properties.readTimeout().toSeconds());
    }

    private static SimpleClientHttpRequestFactory requestFactory(Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return factory;
    }

    public VisionResponse analyzeVision(VisionRequest request) {
        return post("/v1/vision/analyze", request, VisionResponse.class);
    }

    public QuestionResponse nextQuestion(QuestionRequest request) {
        return post("/v1/questions/next", request, QuestionResponse.class);
    }

    public DepartmentResponse classifyDepartment(DepartmentRequest request) {
        return post("/v1/departments/classify", request, DepartmentResponse.class);
    }

    public DraftResponse generateDraft(DraftRequest request) {
        return post("/v1/drafts/generate", request, DraftResponse.class);
    }

    public InsightResponse generateInsight(InsightRequest request) {
        return post("/v1/insights/generate", request, InsightResponse.class);
    }

    private <T> T post(String path, Object body, Class<T> type) {
        long started = System.nanoTime();
        try {
            T response = restClient.post().uri(path).body(body).retrieve().body(type);
            if (response == null) {
                throw new BusinessException(ErrorCode.AI_SERVER_ERROR, "AI 서버가 빈 응답을 반환했습니다: " + path);
            }
            log.debug("AI {} 완료 ({}ms)", path, (System.nanoTime() - started) / 1_000_000);
            return response;
        } catch (RestClientException exception) {
            log.error("AI 서버 호출 실패: {}{} — {}", baseUrl, path, exception.getMessage());
            throw new BusinessException(ErrorCode.AI_SERVER_ERROR,
                    "AI 분석 서버 호출에 실패했습니다 (" + path + "): " + exception.getMessage());
        }
    }

    // ── 요청/응답 계약 (ai-server 의 pydantic 모델과 1:1) ──

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record PhotoPayload(String base64, String mimeType) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record VisionPayload(String detectedHazard, String photoAnalysis, String risk, String exposedTarget,
                                Double objectConfidence, List<String> objectCandidates) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record VisionRequest(String sessionId, String locationText, String description, PhotoPayload photo) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record VisionResponse(String detectedHazard, String photoAnalysis, String risk, String exposedTarget,
                                 String riskLevel, Double objectConfidence, List<String> objectCandidates) {
        public VisionPayload toPayload() {
            return new VisionPayload(detectedHazard, photoAnalysis, risk, exposedTarget,
                    objectConfidence, objectCandidates);
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record QuestionRequest(String sessionId, String locationText, String description, VisionPayload vision,
                                  String qaHistory, int questionIndex, int questionCount, List<String> askedAxes) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record OptionPayload(String value, String label) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record QuestionResponse(String text, List<OptionPayload> options, String axis) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record DepartmentRequest(String sessionId, String locationText, VisionPayload vision) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record DepartmentResponse(String departmentCode, String departmentName, String departmentContact,
                                     String assignedBy, Double confidence, String rationale) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record DraftRequest(String sessionId, String mode, String locationText, String description,
                               VisionPayload vision, String riskLevel, String qaHistory) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record DraftResponse(String title, String hazardPlace, String occurrenceLocation, String hazard,
                                String photoAnalysis, String risk, String exposedTarget, String actionUrgent,
                                String actionFundamental, String otherOpinion, String riskLevelFinal,
                                String riskLevelRationale, Boolean riskLevelChanged, List<String> similarCaseIds,
                                Integer similarCaseCount) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record InsightRequest(String sessionId, String reportId, String locationText, String detectedHazard,
                                 String photoAnalysis, String risk, String riskLevel, String draftJson) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record InsightResponse(String summary, List<String> keywords, Boolean isRecurring, String insight,
                                  String recommendedPriority, List<String> relatedReportIds,
                                  List<String> similarCaseIds, Integer similarCaseCount) {}
}
