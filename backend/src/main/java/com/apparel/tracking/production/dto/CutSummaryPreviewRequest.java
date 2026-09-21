package com.apparel.tracking.production.dto;

import java.math.BigDecimal;
import java.util.List;

import com.apparel.tracking.production.domain.CutType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * What the summary form asks while the totals are being typed.
 *
 * @param newRolls        rolls off a batch: the cut's rolls less any already open
 * @param colorLines      the colours the run is cut in, when it is cut by colour;
 *                        the preview then walks each colour's batches for its own
 *                        line, and the totals above stand for the lines' sums
 */
public record CutSummaryPreviewRequest(
        @NotNull Long fabricTypeId,
        @NotNull CutType cutType,
        @NotNull @DecimalMin("0.001") @Digits(integer = 11, fraction = 3) BigDecimal totalWeight,
        @DecimalMin("0.0") @Digits(integer = 11, fraction = 3) BigDecimal wasteWeight,
        @Min(0) int newRolls,
        @Valid List<CutColorLineRequest> colorLines) {

    public List<CutColorLineRequest> colorLinesOrEmpty() {
        return colorLines == null ? List.of() : colorLines;
    }
}
