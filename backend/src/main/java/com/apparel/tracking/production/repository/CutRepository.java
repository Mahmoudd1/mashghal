package com.apparel.tracking.production.repository;

import java.math.BigDecimal;
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

    /**
     * What a main cut's secondary runs have already charged against it.
     *
     * <p>Their fabric came off rolls this cut drew, so it is spent from the same
     * total rather than taken from the batches again. {@code excludeCutId} leaves
     * the row being edited out of its own headroom check.
     *
     * <p>A secondary run that names the colour it was cut in is not counted: it
     * was drawn off the shelf itself, so charging it here as well would spend the
     * main cut's weight on fabric it never held.
     */
    @Query("""
            select coalesce(sum(c.totalWeight), 0)
            from Cut c
            where c.parentMainCut.id = :parentId
              and c.cutType = com.apparel.tracking.production.domain.CutType.SECONDARY
              and c.entryMode = com.apparel.tracking.production.domain.CutEntryMode.SUMMARY
              and c.fabricColor is null
              and (:excludeCutId is null or c.id <> :excludeCutId)
            """)
    BigDecimal secondaryWeightCharged(
            @Param("parentId") Long parentId, @Param("excludeCutId") Long excludeCutId);

    /** What a detailed cut actually took off the rolls, cut and binned alike. */
    @Query("""
            select coalesce(sum(cr.weightConsumed + cr.wasteWeight), 0)
            from CutRoll cr where cr.cut.id = :cutId
            """)
    BigDecimal rollWeightOffTheShelf(@Param("cutId") Long cutId);

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
