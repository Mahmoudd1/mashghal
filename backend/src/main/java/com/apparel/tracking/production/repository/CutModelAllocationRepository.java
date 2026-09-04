package com.apparel.tracking.production.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.apparel.tracking.production.domain.CutModelAllocation;
import com.apparel.tracking.production.dto.ModelBranchPlanRow;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CutModelAllocationRepository extends JpaRepository<CutModelAllocation, Long> {

    @Query("""
            select a from CutModelAllocation a
              join fetch a.model
              join fetch a.branch
              join fetch a.cut
            where a.cut.id = :cutId
            order by a.model.modelNumber asc, a.branch.sortOrder asc
            """)
    List<CutModelAllocation> findByCut(@Param("cutId") Long cutId);

    @Query("""
            select a from CutModelAllocation a
              join fetch a.cut c
              join fetch a.branch
              join fetch a.model
              join fetch c.branch
            where a.model.id = :modelId
            order by c.cutDate asc, c.cutNumber asc
            """)
    List<CutModelAllocation> findByModel(@Param("modelId") Long modelId);

    Optional<CutModelAllocation> findByCutIdAndModelIdAndBranchId(Long cutId, Long modelId, Long branchId);

    boolean existsByModelId(Long modelId);

    /**
     * Planned pieces per branch for one or all models. Passing null for
     * {@code modelId} returns every model's plan in one query, which is what the
     * model list screen needs.
     */
    @Query("""
            select new com.apparel.tracking.production.dto.ModelBranchPlanRow(
                a.model.id, b.id, b.code, b.nameAr, b.nameEn, sum(a.quantityAllocated))
            from CutModelAllocation a
              join a.branch b
            where (:modelId is null or a.model.id = :modelId)
            group by a.model.id, b.id, b.code, b.nameAr, b.nameEn
            order by b.sortOrder asc
            """)
    List<ModelBranchPlanRow> plannedByModelAndBranch(@Param("modelId") Long modelId);

    /** Planned pieces per branch across every model, for the branch rollups. */
    @Query("""
            select a.branch.id, sum(a.quantityAllocated)
            from CutModelAllocation a
            group by a.branch.id
            """)
    List<Object[]> plannedTotalsByBranch();

    /** Total pieces allocated per cut, for a page of cuts at a time. */
    @Query("""
            select a.cut.id, sum(a.quantityAllocated)
            from CutModelAllocation a
            where a.cut.id in :cutIds
            group by a.cut.id
            """)
    List<Object[]> totalsByCutIds(@Param("cutIds") Collection<Long> cutIds);

    /**
     * How many distinct MAIN cuts feed each model. Anything above one is the rare
     * case the UI calls out so data entry does not read it as a typo.
     */
    @Query("""
            select a.model.id, count(distinct a.cut.id)
            from CutModelAllocation a
            where a.cut.cutType = com.apparel.tracking.production.domain.CutType.MAIN
              and (:modelId is null or a.model.id = :modelId)
            group by a.model.id
            """)
    List<Object[]> mainCutCountsByModel(@Param("modelId") Long modelId);
}
