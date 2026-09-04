package com.apparel.tracking.production.repository;

import java.util.List;
import java.util.Optional;

import com.apparel.tracking.production.domain.CutRoll;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CutRollRepository extends JpaRepository<CutRoll, Long> {

    @Query("""
            select cr from CutRoll cr
              join fetch cr.fabricRoll r
              join fetch r.intake i
              join fetch i.fabricType
              left join fetch r.color
            where cr.cut.id = :cutId
            order by cr.id asc
            """)
    List<CutRoll> findByCut(@Param("cutId") Long cutId);

    Optional<CutRoll> findByCutIdAndFabricRollId(Long cutId, Long fabricRollId);

    boolean existsByFabricRollId(Long fabricRollId);

    /** Fabric consumed per cut and fabric type: [cutId, typeId, typeNameAr, unit, weight]. */
    @Query("""
            select cr.cut.id, t.id, t.nameAr, t.unit, sum(cr.weightConsumed)
            from CutRoll cr
              join cr.fabricRoll r
              join r.intake i
              join i.fabricType t
            group by cr.cut.id, t.id, t.nameAr, t.unit
            """)
    List<Object[]> consumptionByCutAndFabricType();

    /** Layers per cut, for apportioning that consumption: [cutId, layers]. */
    @Query("select cr.cut.id, sum(cr.layers) from CutRoll cr group by cr.cut.id")
    List<Object[]> layersByCut();

    /** Layers, weight and waste totals for a page of cuts: [cutId, layers, consumed, defect]. */
    @Query("""
            select cr.cut.id, sum(cr.layers), sum(cr.weightConsumed), sum(cr.defectWeight)
            from CutRoll cr
            where cr.cut.id in :cutIds
            group by cr.cut.id
            """)
    List<Object[]> totalsByCutIds(@Param("cutIds") List<Long> cutIds);
}
