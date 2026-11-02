package com.dasigochim.ai.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.dasigochim.ai.config.AiServerProperties;
import com.dasigochim.common.exception.BusinessException;
import com.dasigochim.common.exception.ErrorCode;
import com.dasigochim.report.domain.RiskLevel;
import java.util.Base64;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * ai-server(Gemini) 를 실제로 호출하는 {@link AiAnalysisClient} 구현.
 *
 * <p>신고 플로우 한 건은 다음 순서로 AI 서버를 탄다.
 * <pre>
 *   질문 단계 : ① /v1/vision/analyze → ② /v1/questions/next   (답변 하나 받을 때마다 1회)
 *   초안 단계 : (①은 캐시 재사용)     → ④ /v1/drafts/generate → ③ /v1/departments/classify
 *   제출 이후 : (①은 캐시 재사용)     → ⑤ /v1/insights/generate   (비동기, 담당자용)
 * </pre>
 *
 * <p>②는 직전까지의 질문·답변을 이력으로 받아 아직 확인되지 않은 정보를 묻는 대화형
 * 설계다. 그래서 질문을 미리 몰아 만들지 않고, 답변이 들어올 때마다 그 답변을 이력에
 * 넣어 다음 질문을 만든다.
 */
@Component
@ConditionalOnProperty(name = "app.external.ai", havingValue = "http")
public class HttpAiAnalysisClient implements AiAnalysisClient {
    private static final Logger log = LoggerFactory.getLogger(HttpAiAnalysisClient.class);

    /** 프런트가 "질문 세 가지만" 구절을 굵게 강조하므로 문구를 유지한다. */
    private static final String QUESTION_INTRO = "신고서를 작성하기 위한 질문 세 가지만 더 물어볼게요.";

    private final AiServerClient aiServer;
    private final AiVisionCache visionCache;
    private final AiServerProperties properties;
    private final ObjectMapper objectMapper;

    /** 사진 없이 텍스트만 다루는 레거시 엔드포인트용. AI 서버에 대응 경로가 없다. */
    private final MockAiAnalysisClient textFallback = new MockAiAnalysisClient();

    public HttpAiAnalysisClient(AiServerClient aiServer, AiVisionCache visionCache, AiServerProperties properties,
                                ObjectMapper objectMapper) {
        this.aiServer = aiServer;
        this.visionCache = visionCache;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * {@code POST /api/v1/ai/analyze-content} 용. 사진 없이 텍스트만 받는 계약이라
     * ai-server 의 판독 파이프라인에 태울 수 없어 목 응답을 그대로 쓴다.
     * 신고 플로우(질문·초안)는 이 경로를 타지 않는다.
     */
    @Override
    public ContentAnalysis analyzeContent(String description, String location) {
        log.warn("analyzeContent 는 AI 서버 연동 대상이 아니다 (사진 없는 텍스트 전용 계약) — 목 응답을 반환한다.");
        return textFallback.analyzeContent(description, location);
    }

    /** {@code POST /api/v1/ai/draft-from-text} 용. 위와 같은 이유로 목 응답을 쓴다. */
    @Override
    public DraftResult draftFromText(String text) {
        log.warn("draftFromText 는 AI 서버 연동 대상이 아니다 — 목 응답을 반환한다.");
        return textFallback.draftFromText(text);
    }

    @Override
    public QuestionStep createReportQuestion(ReportContext context, List<Answer> answers) {
        int count = properties.questionCount();
        int index = (answers == null ? 0 : answers.size()) + 1;
        if (index > count) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "질문 %d개를 모두 받았습니다.".formatted(count));
        }

        AiServerClient.VisionResponse vision = analyzeVision(context);
        AiServerClient.QuestionResponse response = aiServer.nextQuestion(new AiServerClient.QuestionRequest(
                context.sessionId(), context.locationText(), context.incidentDescription(), vision.toPayload(),
                formatQaHistory(answers), index, count, askedAxes(answers)));

        List<String> options = response.options() == null ? List.of()
                : response.options().stream().map(AiServerClient.OptionPayload::label).toList();

        log.debug("AI 확인 질문 {}/{} 생성 session={}", index, count, context.sessionId());
        return new QuestionStep(index == 1 ? QUESTION_INTRO : null,
                new Question("q" + index, response.text(), options, true), index, count, index == count,
                response.axis());
    }

    @Override
    public ReportDraft createReportDraft(ReportContext context, List<Answer> answers) {
        AiServerClient.VisionResponse vision = analyzeVision(context);

        AiServerClient.DraftResponse draft = aiServer.generateDraft(new AiServerClient.DraftRequest(
                context.sessionId(), "generate", context.locationText(), context.incidentDescription(),
                vision.toPayload(), vision.riskLevel(), formatQaHistory(answers)));

        AiServerClient.DepartmentResponse department = aiServer.classifyDepartment(
                new AiServerClient.DepartmentRequest(context.sessionId(), context.locationText(), vision.toPayload()));

        log.debug("AI 초안 생성 완료 session={} risk={} dept={}",
                context.sessionId(), draft.riskLevelFinal(), department.departmentCode());

        return new ReportDraft(
                draft.title(),
                hazardContent(draft),
                improvementSuggestion(draft),
                toRiskLevel(draft.riskLevelFinal()),
                department.departmentCode(),
                vision.detectedHazard(),
                draft.riskLevelRationale(),
                draft.riskLevelChanged());
    }

    @Override
    public Insight generateInsight(ReportContext context, ConfirmedReport confirmed) {
        AiServerClient.VisionResponse vision = analyzeVision(context);

        AiServerClient.InsightResponse response = aiServer.generateInsight(new AiServerClient.InsightRequest(
                context.sessionId(), confirmed.trackingId(), context.locationText(), vision.detectedHazard(),
                vision.photoAnalysis(), vision.risk(), toAiRiskLevel(confirmed.riskLevel()), draftJson(confirmed)));

        log.debug("AI 인사이트 생성 완료 session={} priority={} recurring={}",
                context.sessionId(), response.recommendedPriority(), response.isRecurring());

        return new Insight(
                response.summary(),
                response.keywords() == null ? List.of() : response.keywords(),
                Boolean.TRUE.equals(response.isRecurring()),
                response.insight(),
                response.recommendedPriority(),
                response.relatedReportIds() == null ? List.of() : response.relatedReportIds(),
                response.similarCaseCount() == null ? 0 : response.similarCaseCount());
    }

    /** ⑤는 확정된 신고서를 JSON 문자열로 받는다. 사용자가 화면에서 고친 내용이 그대로 들어간다. */
    private String draftJson(ConfirmedReport confirmed) {
        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("tracking_id", confirmed.trackingId());
        draft.put("title", confirmed.title());
        draft.put("content", confirmed.content());
        draft.put("risk_level", toAiRiskLevel(confirmed.riskLevel()));
        try {
            return objectMapper.writeValueAsString(draft);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.AI_SERVER_ERROR, "신고 내용을 직렬화하지 못했습니다.");
        }
    }

    private String toAiRiskLevel(RiskLevel riskLevel) {
        return riskLevel == null ? "medium" : riskLevel.name().toLowerCase(Locale.ROOT);
    }

    private AiServerClient.VisionResponse analyzeVision(ReportContext context) {
        AiServerClient.VisionResponse cached = visionCache.get(context.sessionId());
        if (cached != null) return cached;

        Photo photo = context.photo();
        if (photo == null || photo.bytes() == null || photo.bytes().length == 0) {
            throw new BusinessException(ErrorCode.AI_SERVER_ERROR, "사진이 있어야 AI 판독을 할 수 있습니다.");
        }
        AiServerClient.VisionResponse vision = aiServer.analyzeVision(new AiServerClient.VisionRequest(
                context.sessionId(), context.locationText(), context.incidentDescription(),
                new AiServerClient.PhotoPayload(Base64.getEncoder().encodeToString(photo.bytes()), photo.mimeType())));
        visionCache.put(context.sessionId(), vision);
        return vision;
    }

    private String formatQaHistory(List<Answer> answers) {
        if (answers == null || answers.isEmpty()) return null;
        StringJoiner joiner = new StringJoiner("\n");
        int index = 1;
        for (Answer answer : answers) {
            joiner.add("Q%d. %s".formatted(index, answer.question()));
            joiner.add("A%d. %s".formatted(index, answer.answer()));
            index++;
        }
        return joiner.toString();
    }

    private List<String> askedAxes(List<Answer> answers) {
        if (answers == null || answers.isEmpty()) return List.of();
        return answers.stream()
                .map(Answer::axis)
                .filter(axis -> axis != null && !axis.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    /** 신고서의 "안전·보건 유해/위험/시설/장소 내용" 칸. 화면에서 그대로 수정할 수 있어야 해 항목별로 편다. */
    private String hazardContent(AiServerClient.DraftResponse draft) {
        StringJoiner joiner = new StringJoiner("\n");
        appendItem(joiner, "위험 요소", draft.hazard());
        appendItem(joiner, "현장 상태", draft.photoAnalysis());
        appendItem(joiner, "위험성", draft.risk());
        appendItem(joiner, "노출 대상", draft.exposedTarget());
        return joiner.toString();
    }

    /** 신고서의 "개선 제안 사항" 칸. */
    private String improvementSuggestion(AiServerClient.DraftResponse draft) {
        StringJoiner joiner = new StringJoiner("\n");
        appendItem(joiner, "긴급 조치", draft.actionUrgent());
        appendItem(joiner, "근본 대책", draft.actionFundamental());
        appendItem(joiner, "기타 의견", draft.otherOpinion());
        return joiner.toString();
    }

    private void appendItem(StringJoiner joiner, String label, String value) {
        if (value != null && !value.isBlank()) joiner.add("· " + label + ": " + value.trim());
    }

    /**
     * AI 서버는 low/medium/high/critical 4단계를, 백엔드는 LOW/MEDIUM/HIGH 3단계를 쓴다.
     * critical 은 가장 높은 등급인 HIGH 로 접는다.
     */
    private RiskLevel toRiskLevel(String value) {
        return switch (value == null ? "" : value.toLowerCase(Locale.ROOT)) {
            case "low" -> RiskLevel.LOW;
            case "high", "critical" -> RiskLevel.HIGH;
            default -> RiskLevel.MEDIUM;
        };
    }
}
