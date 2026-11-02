package com.dasigochim.internal.service;

import com.dasigochim.department.domain.DepartmentDuty;
import com.dasigochim.department.repository.DepartmentDutyRepository;
import com.dasigochim.internal.dto.DutySearchDto;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * C-1 부서 업무범위 검색.
 *
 * <p><b>검색은 벡터일 필요가 없다.</b> 계약상 필요한 건 후보 목록과 score 뿐이다.
 * 여기서는 ai-server 의 stub(<code>app/backends/stub_client.py</code>)과 <b>같은 문자 bigram
 * 자카드 유사도</b>를 쓴다. 같은 점수 함수를 쓰면 BACKEND_MODE 를 stub↔http 로 바꿔도
 * 후보 순위가 같아서 "stub 에서는 되는데 http 에서는 다르다" 는 상황이 안 생긴다.
 *
 * <p>코퍼스가 15문장이라 전부 읽어 메모리에서 채점한다. 수천 문장이 되면 Postgres
 * 전문검색이나 임베딩으로 갈아끼우면 되고, C-1 계약은 그대로다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DutySearchService {
    private final DepartmentDutyRepository dutyRepository;
    private final JdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true)
    public DutySearchDto.Response search(DutySearchDto.Request request) {
        Set<String> query = bigrams(request.queryText());
        List<DepartmentDuty> duties = dutyRepository.findAllActive();

        List<DutySearchDto.Candidate> candidates = duties.stream()
                .map(duty -> new DutySearchDto.Candidate(
                        duty.getDepartment().getCode(),
                        duty.getDepartment().getName(),
                        duty.getDutyText(),
                        similarity(query, bigrams(duty.getDutyText()))))
                .sorted(Comparator.comparingDouble(DutySearchDto.Candidate::score).reversed())
                .limit(request.topKOrDefault())
                .toList();

        int corpusVersion = corpusVersion();
        log.debug("C-1 duty-search session={} corpus=v{} 후보={}/{}",
                request.sessionId(), corpusVersion, candidates.size(), duties.size());
        return new DutySearchDto.Response(candidates, corpusVersion);
    }

    private int corpusVersion() {
        Integer version = jdbcTemplate.queryForObject(
                "SELECT version FROM department_duty_corpus WHERE id = 1", Integer.class);
        return version == null ? 1 : version;
    }

    /**
     * 문자 bigram 집합. 영문·숫자·한글만 남기고 소문자로 접는다.
     * ai-server stub 의 {@code _bigrams()} 와 동일한 규칙이어야 한다.
     */
    private static Set<String> bigrams(String text) {
        if (text == null) {
            return Set.of();
        }
        String cleaned = Normalizer.normalize(text, Normalizer.Form.NFC)
                .toLowerCase()
                .replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}]", "");
        if (cleaned.length() < 2) {
            return cleaned.isEmpty() ? Set.of() : Set.of(cleaned);
        }
        Set<String> out = new HashSet<>(cleaned.length());
        for (int i = 0; i < cleaned.length() - 1; i++) {
            out.add(cleaned.substring(i, i + 2));
        }
        return out;
    }

    /** 자카드 유사도 = 교집합 / 합집합. 소수 4자리로 자른다 (stub 과 동일). */
    private static double similarity(Set<String> query, Set<String> document) {
        if (query.isEmpty() || document.isEmpty()) {
            return 0.0;
        }
        Set<String> intersection = new HashSet<>(query);
        intersection.retainAll(document);
        if (intersection.isEmpty()) {
            return 0.0;
        }
        Set<String> union = new HashSet<>(query);
        union.addAll(document);
        return Math.round(intersection.size() / (double) union.size() * 10_000d) / 10_000d;
    }
}
