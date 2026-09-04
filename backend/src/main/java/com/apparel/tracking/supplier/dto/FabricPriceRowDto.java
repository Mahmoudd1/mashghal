package com.apparel.tracking.supplier.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

import com.apparel.tracking.fabric.domain.FabricUnit;

/**
 * Price history for one fabric type, optionally narrowed to one supplier.
 *
 * <p>{@code averagePrice} is weighted by quantity — total spent over total bought
 * — not a plain mean of the batch prices. A 2-tonne batch and a 50 kg batch do
 * not tell you the same thing about what the fabric costs, and the weighted
 * figure is the one that reconciles with stock value.
 *
 * @param minPrice    cheapest unit price paid
 * @param maxPrice    dearest unit price paid
 * @param latestPrice the most recent price, for comparison against the average
 */
public record FabricPriceRowDto(
        Long fabricTypeId,
        String fabricTypeNameAr,
        String fabricTypeNameEn,
        FabricUnit unit,
        Long supplierId,
        String supplierNameAr,
        long batchCount,
        BigDecimal totalQuantity,
        BigDecimal totalCost,
        BigDecimal averagePrice,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        BigDecimal latestPrice,
        LocalDate latestDate) {

    /** Divides total spend by total quantity, which is the weighted average. */
    public static FabricPriceRowDto of(
            Long fabricTypeId,
            String nameAr,
            String nameEn,
            FabricUnit unit,
            Long supplierId,
            String supplierNameAr,
            long batchCount,
            BigDecimal totalQuantity,
            BigDecimal totalCost,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            BigDecimal latestPrice,
            LocalDate latestDate) {

        BigDecimal average = totalQuantity == null || totalQuantity.signum() == 0
                ? BigDecimal.ZERO
                : totalCost.divide(totalQuantity, 3, RoundingMode.HALF_UP);

        return new FabricPriceRowDto(
                fabricTypeId, nameAr, nameEn, unit, supplierId, supplierNameAr,
                batchCount, totalQuantity, totalCost, average, minPrice, maxPrice, latestPrice, latestDate);
    }
}
