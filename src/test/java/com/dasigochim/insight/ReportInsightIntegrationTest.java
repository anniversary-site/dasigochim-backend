package com.dasigochim.insight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dasigochim.ai.client.AiAnalysisClient;
import com.dasigochim.ai.service.AiAnalysisService;
import com.dasigochim.common.exception.BusinessException;
import com.dasigochim.department.domain.Department;
import com.dasigochim.department.repository.DepartmentRepository;
import com.dasigochim.insight.service.ReportInsightService;
import com.dasigochim.report.domain.RiskLevel;
import com.dasigochim.report.service.ReportService;
import com.dasigochim.user.domain.User;
import com.dasigochim.user.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ReportInsightIntegrationTest {
    @Autowired UserRepository userRepository;
    @Autowired DepartmentRepository departmentRepository;
    @Autowired ReportService reportService;
    @Autowired AiAnalysisService aiAnalysisService;
    @Autowired ReportInsightService insightService;

    @Test
    void submittedReportGetsInsightAndKeepsRiskRationale() {
        Long reportId = submittedReport("insight-google-1");

        var beforeGeneration = insightService.find(reportId);
        assertThat(beforeGeneration.insight()).isNull();
        assertThat(beforeGeneration.riskLevel()).isEqualTo(RiskLevel.MEDIUM);
        assertThat(beforeGeneration.riskLevelRationale()).isNotBlank();

        var response = insightService.generate(reportId);
        assertThat(response.insight()).isNotNull();
        assertThat(response.insight().summary()).isNotBlank();
        assertThat(response.insight().keywords()).isNotEmpty();
        assertThat(response.insight().recommendedPriority()).isNotNull();
    }

    @Test
    void regeneratingOverwritesTheSameInsight() {
        Long reportId = submittedReport("insight-google-2");

        insightService.generate(reportId);
        var regenerated = insightService.generate(reportId);

        assertThat(regenerated.insight()).isNotNull();
        assertThat(insightService.find(reportId).insight().summary())
                .isEqualTo(regenerated.insight().summary());
    }

    @Test
    void draftReportCannotHaveInsight() {
        User user = userRepository.save(new User("GOOGLE", "insight-google-3", "홍길동"));
        Long reportId = reportService.createDraft(user.getId()).id();

        assertThatThrownBy(() -> insightService.generate(reportId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("제출된 신고에만");
    }

    /** 초안 생성까지 마치고 제출한 신고 하나를 만든다. */
    private Long submittedReport(String providerId) {
        User user = userRepository.save(new User("GOOGLE", providerId, "홍길동"));
        departmentRepository.findByCode("FACILITY")
                .orElseGet(() -> departmentRepository.save(new Department("시설팀", "FACILITY")));
        Long reportId = reportService.createDraft(user.getId()).id();
        aiAnalysisService.createReportDraft(reportId, user.getId(), "로욜라 도서관 3관 입구 앞",
                "바닥 타일이 깨져 있어요.",
                List.of(new AiAnalysisClient.Answer("traffic_impact", "통행 영향", "우회해야 함", null)));
        reportService.submit(reportId, user.getId());
        return reportId;
    }
}
