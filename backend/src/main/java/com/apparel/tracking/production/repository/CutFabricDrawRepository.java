package com.apparel.tracking.production.repository;

import java.math.BigDecimal;
import java.util.List;

import com.apparel.tracking.production.domain.CutFabricDraw;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CutFabricDrawRepository extends JpaRepository<CutFabricDraw, Long> {

    List<CutFabricDraw> findByCutId(Long cutId);

    void deleteByCutId(Long cutId);

    /**
     * Summary cuts' consumption per cut and fabric type, shaped like the roll-based
     * query beside it so the costing report can read both and not care which way a
     * cut was written down.
     *
     * <p>Rows: [cutId, cutType, typeId, typeNameAr, unit, weight].
     */
    @Query("""
            select d.cut.id, c.cutType, t.id, t.nameAr, t.unit, sum(d.weightConsumed)
            from CutFabricDraw d
              join d.cut c
              join d.intake i
              join i.fabricType t
            group by d.cut.id, c.cutType, t.id, t.nameAr, t.unit
            """)
    List<Object[]> consumptionByCutAndFabricType();

    /**
     * Layers stated on each summary cut: [cutId, layers].
     *
     * <p>Read through the draws rather than the cuts, because a summary cut always
     * has draws — the fabric is allocated the moment it is created — and this way
     * the costing report needs no second repository to find them.
     */
    @Query("select distinct d.cut.id, d.cut.totalLayers from CutFabricDraw d")
    List<Object[]> summaryLayersByCut();

    /**
     * What the runs cut from one colour of one batch have taken off it.
     *
     * <p>Read from the draws rather than kept as a counter on the colour row,
     * because the breakdown is a soft record that can be rewritten after the
     * fact — a stored figure would have to be corrected alongside it, and this
     * sum cannot drift. {@code excludeCutId} leaves the run being edited out of
     * its own headroom check.
     */
    @Query("""
            select coalesce(sum(d.weightConsumed + d.wasteWeight), 0)
            from CutFabricDraw d
            where d.intake.id = :intakeId
              and d.cut.fabricColor.id = :colorId
              and (:excludeCutId is null or d.cut.id <> :excludeCutId)
            """)
    BigDecimal weightTakenOfColor(
            @Param("intakeId") Long intakeId,
            @Param("colorId") Long colorId,
            @Param("excludeCutId") Long excludeCutId);

    /** Totals for a page of summary cuts: [cutId, consumed, waste, rolls]. */
    @Query("""
            select d.cut.id, sum(d.weightConsumed), sum(d.wasteWeight), sum(d.rollCount)
            from CutFabricDraw d
            where d.cut.id in :cutIds
            group by d.cut.id
            """)
    List<Object[]> totalsByCutIds(@Param("cutIds") List<Long> cutIds);
}
