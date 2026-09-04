package com.apparel.tracking.pipeline.repository;

import com.apparel.tracking.pipeline.domain.StageMovement;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StageMovementRepository extends JpaRepository<StageMovement, Long> {

    @Query("""
            select m from StageMovement m
              join fetch m.model
              join fetch m.branch
              left join fetch m.fromStage
              left join fetch m.toStage
            where (:modelId is null or m.model.id = :modelId)
              and (:branchId is null or m.branch.id = :branchId)
            """)
    Page<StageMovement> search(@Param("modelId") Long modelId, @Param("branchId") Long branchId, Pageable pageable);
}
