package com.apparel.tracking.production.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.apparel.tracking.fabric.domain.FabricUnit;
import com.apparel.tracking.production.domain.CutType;

/**
 * How much of one fabric type goes into one piece of one model.
 *
 * <p>A cut usually makes several models at once from the same fabric, so its
 * consumption is attributed to each model in proportion to the pieces that model
 * takes off the cut. That is an apportionment, not a measurement: it is the right
 * answer when the models share a marker, and an approximation when their pieces
 * differ greatly in size.
 *
 * <p>Split by cut type, because a garment's fabric arrives in more than one run:
 * the main cut lays out the body, while secondary and derby cuts add further
 * components. Reading them separately shows where the fabric actually goes.
 *
 * <p>Note what {@code weightPerPiece} divides by: the pieces of <em>that</em> cut
 * type. On a main cut that is the garment count; on a secondary cut it is the
 * count of the components that run produced, which is not necessarily the same
 * number. Compare like with like before costing off it.
 *
 * @param weightPerPiece the costing figure — multiply by the fabric's average
 *                       price to get the fabric cost per piece of this run
 */
public record ModelFabricUsageDto(
        Long modelId,
        String modelNumber,
        String modelNameAr,
        Long fabricTypeId,
        String fabricTypeNameAr,
        FabricUnit unit,
        CutType cutType,
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
            CutType cutType,
            long cutCount,
            long totalPieces,
            BigDecimal totalWeight) {

        BigDecimal perPiece = totalPieces == 0
                ? BigDecimal.ZERO
                : totalWeight.divide(BigDecimal.valueOf(totalPieces), 4, RoundingMode.HALF_UP);

        return new ModelFabricUsageDto(
                modelId, modelNumber, modelNameAr, fabricTypeId, fabricTypeNameAr, unit, cutType,
                cutCount, totalPieces, totalWeight, perPiece);
    }
}
