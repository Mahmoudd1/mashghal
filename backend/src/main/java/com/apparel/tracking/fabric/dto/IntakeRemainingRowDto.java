package com.apparel.tracking.fabric.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.apparel.tracking.fabric.domain.FabricPool;
import com.apparel.tracking.fabric.domain.FabricUnit;

/** "How many rolls are left from each date" — one row per intake batch. */
public record IntakeRemainingRowDto(
        Long intakeId,
        Long fabricTypeId,
        String fabricTypeNameAr,
        String fabricTypeNameEn,
        FabricUnit unit,
        FabricPool pool,
        LocalDate intakeDate,
        int totalRolls,
        int remainingRolls,
        BigDecimal totalQuantity,
        BigDecimal remainingQuantity,
        BigDecimal pricePerUnit) {

    /** Blanks the price for anyone but the owner. */
    public IntakeRemainingRowDto withoutPrices() {
        return new IntakeRemainingRowDto(
                intakeId, fabricTypeId, fabricTypeNameAr, fabricTypeNameEn, unit, pool,
                intakeDate, totalRolls, remainingRolls, totalQuantity, remainingQuantity, null);
    }
}
