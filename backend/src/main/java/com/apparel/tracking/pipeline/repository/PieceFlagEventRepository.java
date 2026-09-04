package com.apparel.tracking.pipeline.repository;

import com.apparel.tracking.pipeline.domain.PieceFlagEvent;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PieceFlagEventRepository extends JpaRepository<PieceFlagEvent, Long> {

    @Query("""
            select e from PieceFlagEvent e
              join fetch e.model
              join fetch e.branch
              join fetch e.stage
            where (:modelId is null or e.model.id = :modelId)
              and (:branchId is null or e.branch.id = :branchId)
            """)
    Page<PieceFlagEvent> search(@Param("modelId") Long modelId, @Param("branchId") Long branchId, Pageable pageable);
}
