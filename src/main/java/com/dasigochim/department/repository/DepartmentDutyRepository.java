package com.dasigochim.department.repository;

import com.dasigochim.department.domain.DepartmentDuty;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DepartmentDutyRepository extends JpaRepository<DepartmentDuty, Long> {

    /**
     * 활성 부서의 업무범위 문장 전부. 코퍼스가 15문장 규모라 통째로 읽어 메모리에서 채점한다.
     * 수천 문장이 되면 Postgres 전문검색이나 임베딩으로 갈아끼운다 — C-1 계약은 그대로다.
     */
    @Query("SELECT d FROM DepartmentDuty d JOIN FETCH d.department dept WHERE dept.active = true")
    List<DepartmentDuty> findAllActive();
}
