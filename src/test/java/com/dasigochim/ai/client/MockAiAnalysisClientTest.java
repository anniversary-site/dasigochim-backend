package com.dasigochim.ai.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class MockAiAnalysisClientTest {
    private final MockAiAnalysisClient client = new MockAiAnalysisClient();

    @Test
    void returnsFirstQuestionWithIntroduction() {
        AiAnalysisClient.QuestionStep step =
                client.createReportQuestion(context("로욜라 도서관", "바닥 타일이 깨졌어요."), List.of());

        assertThat(step.introduction()).contains("질문 세 가지만");
        assertThat(step.questionIndex()).isEqualTo(1);
        assertThat(step.questionCount()).isEqualTo(3);
        assertThat(step.last()).isFalse();
        assertThat(step.question().options()).containsExactly("지나갈 수 있음", "우회해야 함", "통행 불가");
        assertThat(step.question().allowCustom()).isTrue();
    }

    @Test
    void advancesToNextQuestionAsAnswersAccumulate() {
        AiAnalysisClient.ReportContext context = context("로욜라 도서관", "바닥 타일이 깨졌어요.");

        AiAnalysisClient.QuestionStep second = client.createReportQuestion(context,
                List.of(answer("traffic_impact", "통행 영향", "우회해야 함")));
        AiAnalysisClient.QuestionStep third = client.createReportQuestion(context,
                List.of(answer("traffic_impact", "통행 영향", "우회해야 함"),
                        answer("duration", "발견 시점", "며칠 됐어요")));

        assertThat(second.introduction()).isNull();
        assertThat(second.questionIndex()).isEqualTo(2);
        assertThat(second.last()).isFalse();
        assertThat(third.questionIndex()).isEqualTo(3);
        assertThat(third.last()).isTrue();
        assertThat(second.question().id()).isNotEqualTo(third.question().id());
    }

    @Test
    void createsTileDamageReportFromExampleAnswers() {
        AiAnalysisClient.ReportDraft result = client.createReportDraft(
                context("로욜라 도서관 3관 입구 앞", "바닥 타일이 깨져서 모서리가 솟아있어요. 걸려 넘어질 뻔했어요."),
                List.of(
                        answer("traffic_impact", "통행에 얼마나 방해가 되나요?", "우회해야 함"),
                        answer("duration", "언제부터 이런 상태였나요?", "며칠 됐어요"),
                        answer("safety_control", "주변에 안전 표시나 통제선이 있나요?", "아무 표시 없어요")));

        assertThat(result.summary()).isEqualTo("로욜라 도서관 3관 입구 앞 바닥 시설 파손");
        assertThat(result.hazardContent())
                .contains("로욜라 도서관 3관 입구 앞")
                .contains("걸려 넘어질 위험")
                .contains("며칠간 같은 상태")
                .contains("안전표시가 없어")
                .contains("우회해 통행");
        assertThat(result.improvementSuggestion()).contains("보수 또는 교체").contains("안전 표시");
    }

    @Test
    void createsDifferentDraftsForLeakAndLightingScenarios() {
        List<AiAnalysisClient.Answer> answers = List.of(
                answer("traffic_impact", "통행 영향", "지나갈 수 있음"),
                answer("duration", "발견 시점", "오늘 처음 봤어요"),
                answer("safety_control", "안전 조치", "표시만 있어요"));

        AiAnalysisClient.ReportDraft leak = client.createReportDraft(
                context("곤자가 플라자 복도", "천장에서 물이 새고 바닥에 물이 고여 있어요."), answers);
        AiAnalysisClient.ReportDraft lighting = client.createReportDraft(
                context("정하상관 뒤편 계단", "가로등 불이 꺼져 너무 어두워요."), answers);

        assertThat(leak.summary()).contains("누수");
        assertThat(leak.improvementSuggestion()).contains("배수");
        assertThat(lighting.summary()).contains("조명 고장");
        assertThat(lighting.improvementSuggestion()).contains("조명");
    }

    private AiAnalysisClient.ReportContext context(String locationText, String incidentDescription) {
        return new AiAnalysisClient.ReportContext("test-session", locationText, incidentDescription, null);
    }

    private AiAnalysisClient.Answer answer(String id, String question, String answer) {
        return new AiAnalysisClient.Answer(id, question, answer);
    }
}
