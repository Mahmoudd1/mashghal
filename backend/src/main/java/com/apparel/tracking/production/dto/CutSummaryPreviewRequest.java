package com.apparel.tracking.production.dto;

import java.math.BigDecimal;

import com.apparel.tracking.production.domain.CutType;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * What the summary form asks while the totals are being typed.
 *
 * @param newRolls        rolls off a batch: the cut's rolls less any already open
 * @param fabricColorId   the colour the run is cut in, when it names one; the
 *                        preview then walks only the batches holding it
 */
public record CutSummaryPreviewRequest(
        @NotNull Long fabricTypeId,
        @NotNull CutType cutType,
        @NotNull @DecimalMin("0.001") @Digits(integer = 11, fraction = 3) BigDecimal totalWeight,
        @DecimalMin("0.0") @Digits(integer = 11, fraction = 3) BigDecimal wasteWeight,
        @Min(0) int newRolls,
        Long fabricColorId) {
}
