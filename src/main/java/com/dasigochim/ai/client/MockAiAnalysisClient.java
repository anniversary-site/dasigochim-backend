package com.dasigochim.ai.client;

import com.dasigochim.report.domain.RiskLevel;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.external.ai", havingValue = "mock", matchIfMissing = true)
public class MockAiAnalysisClient implements AiAnalysisClient {
    /** 목은 고정 질문 3개를 순서대로 하나씩 돌려준다. 답변 개수가 곧 진행 위치다. */
    private static final List<Question> QUESTIONS = List.of(
            new Question("traffic_impact", "통행에 얼마나 방해가 되나요?",
                    List.of("지나갈 수 있음", "우회해야 함", "통행 불가"), true),
            new Question("duration", "언제부터 이런 상태였나요?",
                    List.of("오늘 처음 봤어요", "며칠 됐어요", "한참 됐어요"), true),
            new Question("safety_control", "주변에 안전 표시나 통제선이 있나요?",
                    List.of("아무 표시 없어요", "표시만 있어요", "통제 중이에요"), true));

    @Override
    public ContentAnalysis analyzeContent(String description, String location) {
        String input = description == null || description.isBlank() ? "현장 위험 요소가 발견되었습니다." : description;
        return new ContentAnalysis("[목 분석] " + abbreviate(input, 170), input, RiskLevel.MEDIUM,
                "FACILITY", "[목 분석] 사진 및 설명 기반 시설 위험 가능성");
    }

    @Override
    public DraftResult draftFromText(String text) {
        return new DraftResult("[목 초안] " + abbreviate(text, 170), text);
    }

    @Override
    public QuestionStep createReportQuestion(ReportContext context, List<Answer> answers) {
        int index = (answers == null ? 0 : answers.size()) + 1;
        if (index > QUESTIONS.size()) {
            throw new IllegalStateException("질문 %d개를 모두 받았습니다.".formatted(QUESTIONS.size()));
        }
        return new QuestionStep(
                index == 1 ? "신고서를 작성하기 위한 질문 세 가지만 더 물어볼게요." : null,
                QUESTIONS.get(index - 1), index, QUESTIONS.size(), index == QUESTIONS.size());
    }

    @Override
    public ReportDraft createReportDraft(ReportContext context, List<Answer> answers) {
        String location = fallback(context.locationText(), "위치 미정");
        String incident = fallback(context.incidentDescription(), "시설물 위험 요소가 발견되었습니다.");
        String normalized = incident.toLowerCase(Locale.ROOT);
        String traffic = answer(answers, "traffic_impact", "통행 영향 미확인");
        String duration = answer(answers, "duration", "발견 시점 미확인");
        String safety = answer(answers, "safety_control", "안전 표시 여부 미확인");
        RiskLevel riskLevel = riskLevel(normalized, traffic, safety);
        String riskRationale = riskLevel == RiskLevel.HIGH
                ? "[목 분석] 통행 불가 또는 중대 위험 키워드가 확인되어 높은 등급으로 봄"
                : "[목 분석] 통행 영향과 안전 조치 여부를 근거로 중간 등급으로 봄";

        if (containsAny(normalized, "누수", "물 고임", "물이 고", "미끄")) {
            return new ReportDraft(
                    location + " 누수 및 미끄럼 위험",
                    location + "에 누수 또는 물 고임이 발생해 이용자가 미끄러질 위험이 있습니다. "
                            + context(duration, safety, traffic),
                    "누수 원인 점검과 배수 조치 후 미끄럼 주의 표시 설치를 요청합니다.",
                    riskLevel, "FACILITY", "[목 분석] " + incident, riskRationale, false);
        }
        if (containsAny(normalized, "타일", "보도블록", "바닥", "포트홀", "맨홀")) {
            return new ReportDraft(
                    location + " 바닥 시설 파손",
                    location + " 바닥 타일 또는 보행면이 파손되어 이용자가 걸려 넘어질 위험이 있습니다. "
                            + context(duration, safety, traffic),
                    "파손 부위 보수 또는 교체와 임시 안전 표시 설치를 요청합니다.",
                    riskLevel, "FACILITY", "[목 분석] " + incident, riskRationale, false);
        }
        if (containsAny(normalized, "조명", "가로등", "어두", "불이 꺼")) {
            return new ReportDraft(
                    location + " 조명 고장",
                    location + "의 조명이 작동하지 않아 시야 확보가 어렵고 이동 중 사고 위험이 있습니다. "
                            + context(duration, safety, traffic),
                    "조명 기구와 전원 설비를 점검하고 고장 난 조명을 교체해 주세요.",
                    riskLevel, "FACILITY", "[목 분석] " + incident, riskRationale, false);
        }
        if (containsAny(normalized, "난간", "계단", "손잡이")) {
            return new ReportDraft(
                    location + " 난간·계단 시설 위험",
                    location + "의 난간 또는 계단 시설이 손상되어 추락하거나 넘어질 위험이 있습니다. "
                            + context(duration, safety, traffic),
                    "손상된 난간과 계단을 즉시 점검·보수하고 수리 전까지 접근 통제를 요청합니다.",
                    riskLevel, "FACILITY", "[목 분석] " + incident, riskRationale, false);
        }
        return new ReportDraft(
                location + " 시설 안전 신고",
                location + "에서 다음 위험 요소가 확인되었습니다: " + incident + " "
                        + context(duration, safety, traffic),
                "현장 점검 후 위험 요소를 보수하고 조치 전까지 임시 안전 표시를 설치해 주세요.",
                riskLevel, "FACILITY", "[목 분석] " + incident, riskRationale, false);
    }

    @Override
    public Insight generateInsight(ReportContext context, ConfirmedReport confirmed) {
        return new Insight(
                "[목 인사이트] " + abbreviate(confirmed.title(), 120),
                List.of("목", "시설", "안전"),
                false,
                "[목 인사이트] 유사 과거사례를 조회하지 않는 목 구현이라 반복 발생 여부를 판단하지 않음.",
                "normal",
                List.of(),
                0);
    }

    private String context(String duration, String safety, String traffic) {
        return durationContext(duration) + ", " + safetyContext(safety) + " " + trafficContext(traffic);
    }

    private RiskLevel riskLevel(String normalizedIncident, String traffic, String safety) {
        boolean criticalKeyword = containsAny(normalizedIncident, "붕괴", "화재", "감전", "가스", "추락", "전선 노출");
        boolean blockedWithoutControl = "통행 불가".equals(traffic) && !"통제 중이에요".equals(safety);
        return criticalKeyword || blockedWithoutControl ? RiskLevel.HIGH : RiskLevel.MEDIUM;
    }

    private String durationContext(String value) {
        return switch (value) {
            case "오늘 처음 봤어요" -> "오늘 처음 확인했으며";
            case "며칠 됐어요" -> "며칠간 같은 상태가 이어지고 있으며";
            case "한참 됐어요" -> "상당 기간 같은 상태가 이어지고 있으며";
            default -> "신고자가 '" + value + "'라고 설명했으며";
        };
    }

    private String safetyContext(String value) {
        return switch (value) {
            case "아무 표시 없어요" -> "안전표시가 없어";
            case "표시만 있어요" -> "안전표시만 설치되어 있고";
            case "통제 중이에요" -> "현재 통제 중이며";
            default -> "안전 조치 상태는 '" + value + "'이고";
        };
    }

    private String trafficContext(String value) {
        return switch (value) {
            case "지나갈 수 있음" -> "주의하면 지나갈 수 있는 상태입니다.";
            case "우회해야 함" -> "해당 구간을 우회해 통행하고 있습니다.";
            case "통행 불가" -> "현재 통행이 불가능합니다.";
            default -> "통행 영향은 '" + value + "'로 확인되었습니다.";
        };
    }

    private String answer(List<Answer> answers, String questionId, String fallback) {
        if (answers == null) return fallback;
        return answers.stream()
                .filter(answer -> questionId.equals(answer.questionId()))
                .map(Answer::answer)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(fallback);
    }

    private boolean containsAny(String value, String... keywords) {
        for (String keyword : keywords) {
            if (value.contains(keyword)) return true;
        }
        return false;
    }

    private String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String abbreviate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
