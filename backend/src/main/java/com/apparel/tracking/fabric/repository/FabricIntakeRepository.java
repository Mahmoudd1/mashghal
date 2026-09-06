package com.apparel.tracking.fabric.repository;

import java.util.List;

import com.apparel.tracking.fabric.domain.FabricIntake;
import com.apparel.tracking.fabric.dto.IntakeRemainingRowDto;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FabricIntakeRepository extends JpaRepository<FabricIntake, Long> {

    boolean existsByFabricTypeId(Long fabricTypeId);

    boolean existsByDerbyId(Long derbyId);

    /**
     * The fabric type's most recent regular purchase, newest first.
     *
     * <p>What a derby of that fabric inherits its supplier and price from: the
     * derby is bought alongside the main fabric, so repeating those two figures
     * by hand is transcription, not information. Regular stock only — a derby
     * must not inherit from an earlier derby, or the first one's price would
     * propagate forward for ever.
     */
    @Query("""
            select i from FabricIntake i
              left join fetch i.supplier
            where i.fabricType.id = :fabricTypeId
              and i.derby is null
            order by i.intakeDate desc, i.id desc
            """)
    List<FabricIntake> latestRegular(@Param("fabricTypeId") Long fabricTypeId, Pageable pageable);

    @Query("""
            select i from FabricIntake i
              join fetch i.fabricType
              left join fetch i.derby d
            where (:fabricTypeId is null or i.fabricType.id = :fabricTypeId)
              and (:derbyOnly is null
                   or (:derbyOnly = true and d.id is not null)
                   or (:derbyOnly = false and d.id is null))
              and (:inStockOnly = false or i.consumedRolls < i.totalRolls)
            """)
    Page<FabricIntake> search(
            @Param("fabricTypeId") Long fabricTypeId,
            @Param("derbyOnly") Boolean derbyOnly,
            @Param("inStockOnly") boolean inStockOnly,
            Pageable pageable);

    /**
     * "How many rolls are left from each date." One row per batch, newest first,
     * which is the report the dated-intake model exists to support.
     */
    @Query("""
            select new com.apparel.tracking.fabric.dto.IntakeRemainingRowDto(
                i.id, t.id, t.nameAr, t.nameEn, t.unit,
                case when i.derby is null
                     then com.apparel.tracking.fabric.domain.FabricPool.REGULAR
                     else com.apparel.tracking.fabric.domain.FabricPool.DERBY end,
                i.intakeDate,
                i.totalRolls, i.totalRolls - i.consumedRolls,
                i.totalQuantity, i.totalQuantity - i.consumedQuantity,
                i.pricePerUnit)
            from FabricIntake i
              join i.fabricType t
            where (:fabricTypeId is null or t.id = :fabricTypeId)
              and (:inStockOnly = false or i.consumedRolls < i.totalRolls)
            order by i.intakeDate desc, t.nameAr asc
            """)
    List<IntakeRemainingRowDto> remainingByDate(
            @Param("fabricTypeId") Long fabricTypeId, @Param("inStockOnly") boolean inStockOnly);

    /**
     * Price totals per fabric type.
     * Rows: [typeId, nameAr, nameEn, unit, batches, totalQty, totalCost, minPrice, maxPrice].
     */
    @Query("""
            select t.id, t.nameAr, t.nameEn, t.unit,
                   count(i.id), sum(i.totalQuantity),
                   sum(i.totalQuantity * i.pricePerUnit),
                   min(i.pricePerUnit), max(i.pricePerUnit)
            from FabricIntake i
              join i.fabricType t
            where (:fabricTypeId is null or t.id = :fabricTypeId)
            group by t.id, t.nameAr, t.nameEn, t.unit
            order by t.nameAr asc
            """)
    List<Object[]> priceTotals(@Param("fabricTypeId") Long fabricTypeId);

    /**
     * The same, split by who supplied it — the view that answers which provider
     * is cheaper for a given fabric.
     * Rows: [typeId, nameAr, nameEn, unit, supplierId, supplierName, batches,
     *        totalQty, totalCost, minPrice, maxPrice].
     */
    @Query("""
            select t.id, t.nameAr, t.nameEn, t.unit, s.id, s.nameAr,
                   count(i.id), sum(i.totalQuantity),
                   sum(i.totalQuantity * i.pricePerUnit),
                   min(i.pricePerUnit), max(i.pricePerUnit)
            from FabricIntake i
              join i.fabricType t
              left join i.supplier s
            where (:fabricTypeId is null or t.id = :fabricTypeId)
              and (:supplierId is null or s.id = :supplierId)
            group by t.id, t.nameAr, t.nameEn, t.unit, s.id, s.nameAr
            order by t.nameAr asc, s.nameAr asc
            """)
    List<Object[]> priceTotalsBySupplier(
            @Param("fabricTypeId") Long fabricTypeId, @Param("supplierId") Long supplierId);

    /** Most recent purchase per fabric type and supplier: [typeId, supplierId, date, price]. */
    @Query("""
            select t.id, s.id, i.intakeDate, i.pricePerUnit
            from FabricIntake i
              join i.fabricType t
              left join i.supplier s
            where (:fabricTypeId is null or t.id = :fabricTypeId)
              and (:supplierId is null or s.id = :supplierId)
            order by i.intakeDate desc, i.id desc
            """)
    List<Object[]> latestPrices(
            @Param("fabricTypeId") Long fabricTypeId, @Param("supplierId") Long supplierId);

    /** Remaining per fabric type: [typeId, nameAr, nameEn, unit, batches, totalRolls, remRolls, totalQty, remQty]. */
    @Query("""
            select t.id, t.nameAr, t.nameEn, t.unit,
                   count(i.id),
                   sum(i.totalRolls), sum(i.totalRolls - i.consumedRolls),
                   sum(i.totalQuantity), sum(i.totalQuantity - i.consumedQuantity)
            from FabricIntake i
              join i.fabricType t
            where (:fabricTypeId is null or t.id = :fabricTypeId)
            group by t.id, t.nameAr, t.nameEn, t.unit
            order by t.nameAr asc
            """)
    List<Object[]> remainingByType(@Param("fabricTypeId") Long fabricTypeId);

    /** The same, split by intake date. */
    @Query("""
            select t.id, t.nameAr, t.nameEn, t.unit, i.intakeDate,
                   count(i.id),
                   sum(i.totalRolls), sum(i.totalRolls - i.consumedRolls),
                   sum(i.totalQuantity), sum(i.totalQuantity - i.consumedQuantity)
            from FabricIntake i
              join i.fabricType t
            where (:fabricTypeId is null or t.id = :fabricTypeId)
            group by t.id, t.nameAr, t.nameEn, t.unit, i.intakeDate
            order by t.nameAr asc, i.intakeDate desc
            """)
    List<Object[]> remainingByDateGrouped(@Param("fabricTypeId") Long fabricTypeId);

    /** The same, split by supplier. */
    @Query("""
            select t.id, t.nameAr, t.nameEn, t.unit, s.id, s.nameAr,
                   count(i.id),
                   sum(i.totalRolls), sum(i.totalRolls - i.consumedRolls),
                   sum(i.totalQuantity), sum(i.totalQuantity - i.consumedQuantity)
            from FabricIntake i
              join i.fabricType t
              left join i.supplier s
            where (:fabricTypeId is null or t.id = :fabricTypeId)
            group by t.id, t.nameAr, t.nameEn, t.unit, s.id, s.nameAr
            order by t.nameAr asc, s.nameAr asc
            """)
    List<Object[]> remainingBySupplier(@Param("fabricTypeId") Long fabricTypeId);

    /** Pool totals per fabric type: [typeId, isDerby, batches, totalRolls, remainingRolls, totalQty, remainingQty, cost]. */
    @Query("""
            select t.id,
                   case when i.derby is null then false else true end,
                   count(i.id),
                   sum(i.totalRolls),
                   sum(i.totalRolls - i.consumedRolls),
                   sum(i.totalQuantity),
                   sum(i.totalQuantity - i.consumedQuantity),
                   sum(i.totalQuantity * i.pricePerUnit)
            from FabricIntake i
              join i.fabricType t
            where (:fabricTypeId is null or t.id = :fabricTypeId)
            group by t.id, case when i.derby is null then false else true end
            """)
    List<Object[]> poolTotals(@Param("fabricTypeId") Long fabricTypeId);

    /** Colour breakdown per pool: [typeId, isDerby, colorId, rolls, quantity]. */
    @Query("""
            select t.id,
                   case when i.derby is null then false else true end,
                   c.id, c.nameAr, c.nameEn,
                   sum(b.rollCount), sum(coalesce(b.quantity, 0))
            from FabricIntakeColor b
              join b.intake i
              join i.fabricType t
              join b.color c
            where (:fabricTypeId is null or t.id = :fabricTypeId)
            group by t.id, case when i.derby is null then false else true end, c.id, c.nameAr, c.nameEn
            order by c.nameAr asc
            """)
    List<Object[]> colorAssignments(@Param("fabricTypeId") Long fabricTypeId);
}
