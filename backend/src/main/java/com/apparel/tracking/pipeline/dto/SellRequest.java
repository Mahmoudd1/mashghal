package com.apparel.tracking.pipeline.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

/** Moves received pieces to SOLD. Flagged pieces are non-sellable and stay put. */
public record SellRequest(
        @NotNull Long modelId,
        @NotNull Long branchId,
        @NotNull @Min(1) Integer quantity,
        /**
         * Which size these pieces are. Optional: omitted, the action applies to
         * the pieces recorded without a size, which is what a model that was
         * never entered size by size has.
         */
        Long garmentSizeId,
        @NotNull @PastOrPresent LocalDate soldDate,
        @Size(max = 512) String note) {
}
