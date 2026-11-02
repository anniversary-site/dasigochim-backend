package com.dasigochim.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.dasigochim.ai.client.AiAnalysisClient;
import com.dasigochim.ai.service.AiAnalysisService;
import com.dasigochim.department.domain.Department;
import com.dasigochim.department.repository.DepartmentRepository;
import com.dasigochim.report.domain.RiskLevel;
import com.dasigochim.report.service.ReportService;
import com.dasigochim.user.domain.User;
import com.dasigochim.user.repository.UserRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AiReportFlowIntegrationTest {
    @Autowired UserRepository userRepository;
    @Autowired DepartmentRepository departmentRepository;
    @Autowired ReportService reportService;
    @Autowired AiAnalysisService aiAnalysisService;

    @Test
    void ownedDraftCanRequestQuestionsAndGenerateReportDraft() {
        User user = userRepository.save(new User("GOOGLE", "ai-flow-google", "홍길동"));
        departmentRepository.save(new Department("시설팀", "FACILITY"));
        Long reportId = reportService.createDraft(user.getId()).id();
        reportService.updateLocation(reportId, user.getId(), new ReportService.LocationRequest(
                null, "공용 복도 엘리베이터 앞", null, null, null, null, null,
                null, null, null, null, null, null, null, null, null));

        // 화면과 같은 순서로: 질문 하나 받고 답한 뒤 그 답변을 다음 질문 요청에 실어 보낸다.
        List<String> replies = List.of("우회해야 함", "며칠 됐어요", "아무 표시 없어요");
        List<AiAnalysisClient.Answer> answers = new ArrayList<>();
        AiAnalysisClient.QuestionStep step;
        do {
            step = aiAnalysisService.createReportQuestion(
                    reportId, user.getId(), "바닥 타일이 깨져 있어요.", answers);
            answers.add(new AiAnalysisClient.Answer(step.question().id(), step.question().text(),
                    replies.get(step.questionIndex() - 1), null));
        } while (!step.last());

        AiAnalysisClient.ReportDraft draft = aiAnalysisService.createReportDraft(
                reportId,
                user.getId(),
                "공용 복도 엘리베이터 앞",
                "바닥 타일이 깨져 있어요.",
                answers);

        assertThat(answers).hasSize(3);
        assertThat(step.questionCount()).isEqualTo(3);
        assertThat(draft.hazardContent()).contains("공용 복도 엘리베이터 앞").contains("안전표시가 없어");
        assertThat(reportService.getOwnedDraft(reportId, user.getId()).getLocationDescription())
                .isEqualTo("공용 복도 엘리베이터 앞");
        assertThat(reportService.getOwnedDraft(reportId, user.getId()).getRiskLevel()).isEqualTo(RiskLevel.MEDIUM);
        assertThat(reportService.getOwnedDraft(reportId, user.getId()).getDepartment().getCode()).isEqualTo("FACILITY");
    }
}
