package com.apparel.tracking.fabric.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.apparel.tracking.fabric.domain.FabricUnit;

/**
 * How much of a fabric is left, at one of three levels of detail.
 *
 * <p>{@code intakeDate} and the supplier fields are populated only by the
 * grouping that asks for them, so the same row shape serves all three views and
 * the UI switches columns rather than endpoints.
 */
public record RemainingRowDto(
        Long fabricTypeId,
        String fabricTypeNameAr,
        String fabricTypeNameEn,
        FabricUnit unit,
        LocalDate intakeDate,
        Long supplierId,
        String supplierNameAr,
        long batchCount,
        long totalRolls,
        long remainingRolls,
        BigDecimal totalQuantity,
        BigDecimal remainingQuantity) {
}
