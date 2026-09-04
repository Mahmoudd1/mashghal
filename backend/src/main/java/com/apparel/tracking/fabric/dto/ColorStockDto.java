package com.apparel.tracking.fabric.dto;

import java.math.BigDecimal;

/**
 * A colour's share of a pool, as far as the breakdown records it.
 *
 * <p>Indicative rather than authoritative: fabric can be taken from a batch
 * before its colours are known, so these will not always reconcile to the pool.
 */
public record ColorStockDto(
        Long colorId,
        String colorNameAr,
        String colorNameEn,
        int assignedRolls,
        BigDecimal assignedQuantity,
        int consumedRolls,
        BigDecimal consumedQuantity) {

    public ColorStockDto {
        assignedQuantity = assignedQuantity == null ? BigDecimal.ZERO : assignedQuantity;
        consumedQuantity = consumedQuantity == null ? BigDecimal.ZERO : consumedQuantity;
    }

    public int remainingRolls() {
        return assignedRolls - consumedRolls;
    }
}
