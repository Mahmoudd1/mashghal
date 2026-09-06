package com.apparel.tracking.fabric.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.apparel.tracking.fabric.domain.FabricIntake;
import com.apparel.tracking.fabric.domain.FabricPool;
import com.apparel.tracking.fabric.domain.FabricUnit;

/**
 * The colour breakdown is soft, so it can fall short of the batch total or exceed
 * it. Those are different mistakes and get their own fields — exactly one of them
 * is ever non-zero. Both are advisory: neither blocks a save.
 *
 * <p>{@code finished} says every roll is off the batch, and only then does
 * {@code wasteQuantity} mean anything: until the last roll goes, the unconsumed
 * weight is stock, not loss.
 *
 * @param assignedRolls     rolls named in the colour breakdown
 * @param unassignedRolls   rolls not yet given a colour
 * @param overAssignedRolls rolls assigned beyond what the batch holds
 * @param finished          every roll has come off the batch
 * @param wasteQuantity     fabric bought but never cut; zero until finished
 * @param wastePercentage   that waste as a share of the batch total, to two places
 */
public record FabricIntakeDto(
        Long id,
        Long fabricTypeId,
        String fabricTypeNameAr,
        String fabricTypeNameEn,
        FabricUnit unit,
        FabricPool pool,
        Long supplierId,
        String supplierNameAr,
        LocalDate intakeDate,
        int totalRolls,
        int consumedRolls,
        int remainingRolls,
        BigDecimal totalQuantity,
        BigDecimal consumedQuantity,
        BigDecimal remainingQuantity,
        boolean finished,
        BigDecimal wasteQuantity,
        BigDecimal wastePercentage,
        BigDecimal pricePerUnit,
        BigDecimal totalCost,
        int assignedRolls,
        int unassignedRolls,
        int overAssignedRolls,
        String note,
        List<FabricIntakeColorDto> colorBreakdown) {

    /** Blanks the money for anyone but the owner. */
    public FabricIntakeDto withoutPrices() {
        return new FabricIntakeDto(
                id, fabricTypeId, fabricTypeNameAr, fabricTypeNameEn, unit, pool,
                supplierId, supplierNameAr, intakeDate,
                totalRolls, consumedRolls, remainingRolls,
                totalQuantity, consumedQuantity, remainingQuantity,
                finished, wasteQuantity, wastePercentage,
                null, null,
                assignedRolls, unassignedRolls, overAssignedRolls, note, colorBreakdown);
    }

    public static FabricIntakeDto from(FabricIntake intake) {
        int assigned = intake.assignedRolls();
        return new FabricIntakeDto(
                intake.getId(),
                intake.getFabricType().getId(),
                intake.getFabricType().getNameAr(),
                intake.getFabricType().getNameEn(),
                intake.getFabricType().getUnit(),
                intake.isDerbyPool() ? FabricPool.DERBY : FabricPool.REGULAR,
                intake.getSupplier() == null ? null : intake.getSupplier().getId(),
                intake.getSupplier() == null ? null : intake.getSupplier().getNameAr(),
                intake.getIntakeDate(),
                intake.getTotalRolls(),
                intake.getConsumedRolls(),
                intake.remainingRolls(),
                intake.getTotalQuantity(),
                intake.getConsumedQuantity(),
                intake.remainingQuantity(),
                intake.isFinished(),
                intake.wasteQuantity(),
                intake.wastePercentage(),
                intake.getPricePerUnit(),
                intake.totalCost(),
                assigned,
                Math.max(0, intake.getTotalRolls() - assigned),
                Math.max(0, assigned - intake.getTotalRolls()),
                intake.getNote(),
                intake.getColorBreakdown().stream()
                        .sorted((a, b) -> a.getColor().getNameAr().compareTo(b.getColor().getNameAr()))
                        .map(FabricIntakeColorDto::from)
                        .toList());
    }
}
