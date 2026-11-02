package com.dasigochim.ai.controller;

import com.dasigochim.ai.client.AiAnalysisClient;
import com.dasigochim.ai.service.AiAnalysisService;
import com.dasigochim.common.response.ApiResponse;
import com.dasigochim.common.security.SecurityPrincipal;
import com.dasigochim.report.dto.ReportResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiController {
    private final AiAnalysisService service;

    @PostMapping("/analyze-location")
    ApiResponse<List<AiAnalysisService.LocationCandidate>> location(@Valid @RequestBody LocationRequest request) {
        return ApiResponse.ok(service.analyzeLocation(request.lat(), request.lng()));
    }

    @PostMapping("/analyze-content")
    ApiResponse<ReportResponse> content(@Valid @RequestBody ContentRequest request,
                                        @AuthenticationPrincipal SecurityPrincipal principal) {
        return ApiResponse.ok(service.analyzeContent(request.reportId(), principal.id(), request.description()));
    }

    @PostMapping("/draft-from-text")
    ApiResponse<AiAnalysisClient.DraftResult> draft(@Valid @RequestBody DraftRequest request) {
        return ApiResponse.ok(service.draftFromText(request.text()));
    }

    /**
     * 확인 질문을 하나씩 받아간다. 지금까지의 질문·답변을 함께 보내면 그 내용을 반영해
     * 다음 질문을 만든다. 첫 호출은 {@code answers} 를 비우거나 생략한다.
     */
    @PostMapping("/report-flow/next-question")
    ApiResponse<AiAnalysisClient.QuestionStep> reportQuestion(
            @Valid @RequestBody ReportQuestionRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        return ApiResponse.ok(service.createReportQuestion(
                request.reportId(), principal.id(), request.incidentDescription(), toAnswers(request.answers())));
    }

    @PostMapping("/report-flow/draft")
    ApiResponse<AiAnalysisClient.ReportDraft> reportDraft(
            @Valid @RequestBody ReportDraftRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        return ApiResponse.ok(service.createReportDraft(request.reportId(), principal.id(),
                request.locationDescription(), request.incidentDescription(), toAnswers(request.answers())));
    }

    private static List<AiAnalysisClient.Answer> toAnswers(List<ReportAnswerRequest> answers) {
        if (answers == null) return List.of();
        return answers.stream()
                .map(answer -> new AiAnalysisClient.Answer(
                        answer.questionId(), answer.question(), answer.answer(), answer.axis()))
                .toList();
    }

    record LocationRequest(@NotNull BigDecimal lat, @NotNull BigDecimal lng) {}
    record ContentRequest(@NotNull Long reportId, String description) {}
    record DraftRequest(@NotBlank String text) {}
    record ReportQuestionRequest(@NotNull Long reportId, @NotBlank String incidentDescription,
                                 List<@Valid ReportAnswerRequest> answers) {}
    record ReportDraftRequest(@NotNull Long reportId, @NotBlank String locationDescription,
                              @NotBlank String incidentDescription,
                              @NotEmpty List<@Valid ReportAnswerRequest> answers) {}
    record ReportAnswerRequest(@NotBlank String questionId, @NotBlank String question, @NotBlank String answer,
                               String axis) {}
}
