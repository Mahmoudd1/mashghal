package com.apparel.tracking.production.repository;

import java.util.List;

import com.apparel.tracking.production.domain.Cut;
import com.apparel.tracking.production.domain.CutStatus;
import com.apparel.tracking.production.domain.CutType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CutRepository extends JpaRepository<Cut, Long> {

    boolean existsByCutNumberIgnoreCase(String cutNumber);

    boolean existsByParentMainCutId(Long parentId);

    List<Cut> findAllByParentMainCutIdOrderByCutDateAsc(Long parentId);

    @Query("""
            select c from Cut c
              join fetch c.branch
              left join fetch c.parentMainCut
            where (:cutType is null or c.cutType = :cutType)
              and (:status is null or c.status = :status)
              and (:branchId is null or c.branch.id = :branchId)
              and (:modelId is null or exists (
                    select 1 from CutModelAllocation a
                    where a.cut = c and a.model.id = :modelId))
            """)
    Page<Cut> search(
            @Param("cutType") CutType cutType,
            @Param("status") CutStatus status,
            @Param("branchId") Long branchId,
            @Param("modelId") Long modelId,
            Pageable pageable);
}
