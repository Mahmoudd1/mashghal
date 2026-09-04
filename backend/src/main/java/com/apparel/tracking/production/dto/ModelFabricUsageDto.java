package com.apparel.tracking.production.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.apparel.tracking.fabric.domain.FabricUnit;

/**
 * How much of one fabric type goes into one piece of one model.
 *
 * <p>A cut usually makes several models at once from the same fabric, so its
 * consumption is attributed to each model in proportion to the pieces that model
 * takes off the cut. That is an apportionment, not a measurement: it is the right
 * answer when the models share a marker, and an approximation when their pieces
 * differ greatly in size.
 *
 * @param weightPerPiece the costing figure — multiply by the fabric's average
 *                       price to get the fabric cost of one garment
 */
public record ModelFabricUsageDto(
        Long modelId,
        String modelNumber,
        String modelNameAr,
        Long fabricTypeId,
        String fabricTypeNameAr,
        FabricUnit unit,
        long cutCount,
        long totalPieces,
        BigDecimal totalWeight,
        BigDecimal weightPerPiece) {

    public static ModelFabricUsageDto of(
            Long modelId,
            String modelNumber,
            String modelNameAr,
            Long fabricTypeId,
            String fabricTypeNameAr,
            FabricUnit unit,
            long cutCount,
            long totalPieces,
            BigDecimal totalWeight) {

        BigDecimal perPiece = totalPieces == 0
                ? BigDecimal.ZERO
                : totalWeight.divide(BigDecimal.valueOf(totalPieces), 4, RoundingMode.HALF_UP);

        return new ModelFabricUsageDto(
                modelId, modelNumber, modelNameAr, fabricTypeId, fabricTypeNameAr, unit,
                cutCount, totalPieces, totalWeight, perPiece);
    }
}
