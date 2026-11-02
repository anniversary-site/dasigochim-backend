package com.dasigochim.internal.controller;

import com.dasigochim.internal.dto.CaseSearchDto;
import com.dasigochim.internal.dto.DepartmentResolveDto;
import com.dasigochim.internal.dto.DutySearchDto;
import com.dasigochim.internal.service.CaseSearchService;
import com.dasigochim.internal.service.DepartmentResolveService;
import com.dasigochim.internal.service.DutySearchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 서버(FastAPI)가 부르는 내부 API. C-1 / C-2 / C-5.
 *
 * <p>인증은 {@code X-Agent-Token} 이다 (SecurityConfig 의 internalChain).
 * 브라우저가 부를 경로가 아니라 CORS 를 열지 않는다.
 *
 * <p><b>응답을 ApiResponse 로 감싸지 않는다.</b> AI 서버는 본문을 그대로 파싱해
 * {@code candidates} / {@code department} / {@code cases} 를 꺼낸다
 * (ai-server/app/backends/http_client.py). 감싸면 계약이 깨진다.
 */
@RestController
@RequestMapping("/internal/v1")
@RequiredArgsConstructor
public class InternalAiController {
    private final DutySearchService dutySearchService;
    private final DepartmentResolveService departmentResolveService;
    private final CaseSearchService caseSearchService;

    /** C-1. 실패하면 ③ 전체가 502 로 실패한다 — 부서 판정의 필수 입력이다. */
    @PostMapping("/rag/duty-search")
    DutySearchDto.Response dutySearch(@Valid @RequestBody DutySearchDto.Request request) {
        return dutySearchService.search(request);
    }

    /** C-2. LLM 이 고른 코드를 검증하고 임계값 미달이면 기본 부서로 강등한다. */
    @PostMapping("/departments/resolve")
    DepartmentResolveDto.Response resolveDepartment(@RequestBody DepartmentResolveDto.Request request) {
        return departmentResolveService.resolve(request);
    }

    /** C-5. 부가 정보라 실패해도 ④⑤ 는 "참고 없이 작성" 으로 진행한다. */
    @PostMapping("/cases/search")
    CaseSearchDto.Response searchCases(@RequestBody CaseSearchDto.Request request) {
        return caseSearchService.search(request);
    }
}
