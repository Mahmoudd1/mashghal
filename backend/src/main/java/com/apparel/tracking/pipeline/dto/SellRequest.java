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
        @NotNull @PastOrPresent LocalDate soldDate,
        @Size(max = 512) String note) {
}
