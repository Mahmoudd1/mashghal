package com.apparel.tracking.fabric.repository;

import java.util.List;

import com.apparel.tracking.fabric.domain.FabricRoll;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FabricRollRepository extends JpaRepository<FabricRoll, Long> {

    boolean existsByColorId(Long colorId);

    /** Open rolls a cut can still pick up, newest batch first. */
    @Query("""
            select r from FabricRoll r
              join fetch r.intake i
              join fetch i.fabricType
              left join fetch r.color
            where r.closed = false
              and (:fabricTypeId is null or i.fabricType.id = :fabricTypeId)
              and (:derbyOnly is null
                   or (:derbyOnly = true and i.derby.id is not null)
                   or (:derbyOnly = false and i.derby.id is null))
            order by i.intakeDate desc, r.id asc
            """)
    List<FabricRoll> findOpenRolls(
            @Param("fabricTypeId") Long fabricTypeId, @Param("derbyOnly") Boolean derbyOnly);

    /**
     * How many rolls are sitting part-used right now, per fabric type and colour.
     * Rows: [typeId, typeNameAr, colorId, colorNameAr, openRolls, remainingWeight].
     */
    @Query("""
            select t.id, t.nameAr, c.id, c.nameAr, count(r.id), sum(r.remainingWeight)
            from FabricRoll r
              join r.intake i
              join i.fabricType t
              left join r.color c
            where r.closed = false
              and (:fabricTypeId is null or t.id = :fabricTypeId)
            group by t.id, t.nameAr, c.id, c.nameAr
            order by t.nameAr asc
            """)
    List<Object[]> openRollSummary(@Param("fabricTypeId") Long fabricTypeId);

    /**
     * Weight and finished-roll counts attributed to a colour, for the stock
     * breakdown: [typeId, isDerby, colorId, closedRolls, consumedWeight].
     */
    @Query("""
            select t.id,
                   case when i.derby.id is null then false else true end,
                   c.id,
                   sum(case when r.closed then 1 else 0 end),
                   sum(r.initialWeight - r.remainingWeight)
            from FabricRoll r
              join r.intake i
              join i.fabricType t
              join r.color c
            where (:fabricTypeId is null or t.id = :fabricTypeId)
            group by t.id, case when i.derby.id is null then false else true end, c.id
            """)
    List<Object[]> colorConsumption(@Param("fabricTypeId") Long fabricTypeId);

    /**
     * Roll count per colour for a fabric type, optionally narrowed to one intake
     * date. Rows: [colorId, colorNameAr, rolls, remainingWeight].
     */
    @Query("""
            select c.id, c.nameAr, count(r.id), sum(r.remainingWeight)
            from FabricRoll r
              join r.intake i
              left join r.color c
            where (:fabricTypeId is null or i.fabricType.id = :fabricTypeId)
              and (:intakeDate is null or i.intakeDate = :intakeDate)
              and (:openOnly = false or r.closed = false)
            group by c.id, c.nameAr
            order by c.nameAr asc
            """)
    List<Object[]> rollCountByColor(
            @Param("fabricTypeId") Long fabricTypeId,
            @Param("intakeDate") java.time.LocalDate intakeDate,
            @Param("openOnly") boolean openOnly);
}
