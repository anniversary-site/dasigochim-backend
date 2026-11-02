package com.dasigochim.internal.service;

import com.dasigochim.department.domain.Department;
import com.dasigochim.department.repository.DepartmentRepository;
import com.dasigochim.internal.dto.DepartmentResolveDto;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * C-2 부서 코드 검증 + 임계값 fallback.
 *
 * <p><b>LLM 이 지어낸 부서 코드를 그대로 믿지 않기 위한 관문이다.</b>
 * 판정은 결정론이라 LLM 이 아니라 여기서 한다.
 *
 * <pre>
 * if  코드가 비었거나 / 테이블에 없거나 / active=false 이거나 / confidence &lt; 임계값
 * then 기본 부서로 강등,  assigned_by="fallback",  confidence=null
 * else assigned_by="agent",  confidence=요청값 그대로
 * </pre>
 */
@Slf4j
@Service
public class DepartmentResolveService {
    private final DepartmentRepository departmentRepository;
    private final double threshold;
    private final String defaultCode;

    public DepartmentResolveService(DepartmentRepository departmentRepository,
                                    @Value("${app.ai.department-confidence-threshold:0.7}") double threshold,
                                    @Value("${app.ai.default-department-code:SAFETY_CENTER}") String defaultCode) {
        this.departmentRepository = departmentRepository;
        this.threshold = threshold;
        this.defaultCode = defaultCode;
    }

    @Transactional(readOnly = true)
    public DepartmentResolveDto.Response resolve(DepartmentResolveDto.Request request) {
        String code = request.departmentCode() == null ? "" : request.departmentCode().trim();
        double confidence = request.confidenceOrZero();

        Optional<Department> picked = code.isEmpty()
                ? Optional.empty()
                : departmentRepository.findByCode(code).filter(Department::isActive);

        if (picked.isPresent() && confidence >= threshold) {
            log.debug("C-2 resolve session={} → {} (agent, confidence={})",
                    request.sessionId(), code, confidence);
            return DepartmentResolveDto.Response.agent(
                    payload(picked.get()), confidence, nullToEmpty(request.rationale()));
        }

        String reason = picked.isEmpty() ? "유효하지 않은 부서 코드" : "판정 신뢰도 임계값 미달";
        Department fallback = departmentRepository.findByCode(defaultCode)
                .orElseThrow(() -> new IllegalStateException(
                        "기본 부서 코드가 departments 에 없다: " + defaultCode));

        log.info("C-2 resolve session={} fallback 사유={} (요청 코드='{}', confidence={})",
                request.sessionId(), reason, code, confidence);

        // rationale 은 신고서 초안에 그대로 노출되므로 강등 사유를 사람이 읽을 문장으로 남긴다.
        return DepartmentResolveDto.Response.fallback(payload(fallback),
                "%s으로 기본 부서에 배정함. (모델 판정: %s)"
                        .formatted(reason, request.rationale() == null || request.rationale().isBlank()
                                ? "없음" : request.rationale()));
    }

    private DepartmentResolveDto.DepartmentPayload payload(Department department) {
        return new DepartmentResolveDto.DepartmentPayload(
                department.getCode(), department.getName(), nullToEmpty(department.getContact()));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
