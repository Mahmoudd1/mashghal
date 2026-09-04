package com.apparel.tracking.fabric.dto;

import java.math.BigDecimal;
import java.util.List;

import com.apparel.tracking.fabric.domain.FabricPool;
import com.apparel.tracking.fabric.domain.FabricUnit;

/**
 * Authoritative stock for one fabric type in one pool, summed over its batches.
 *
 * <p>The colour rows underneath are indicative: the breakdown is soft, so they
 * may not add up to the pool totals. The pool figures are the real ones.
 */
public record FabricStockDto(
        Long fabricTypeId,
        String fabricTypeNameAr,
        String fabricTypeNameEn,
        FabricUnit unit,
        FabricPool pool,
        int batchCount,
        int totalRolls,
        int remainingRolls,
        BigDecimal totalQuantity,
        BigDecimal remainingQuantity,
        BigDecimal totalCost,
        List<ColorStockDto> colors,
        int unassignedRolls) {

    /** Blanks the valuation for anyone but the owner. */
    public FabricStockDto withoutPrices() {
        return new FabricStockDto(
                fabricTypeId, fabricTypeNameAr, fabricTypeNameEn, unit, pool,
                batchCount, totalRolls, remainingRolls, totalQuantity, remainingQuantity,
                null, colors, unassignedRolls);
    }
}
