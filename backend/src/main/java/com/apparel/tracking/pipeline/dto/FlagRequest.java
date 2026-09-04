package com.apparel.tracking.pipeline.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

/**
 * Marks pieces defective, or clears the mark.
 *
 * <p>{@code stageCode} defaults to RECEIVED when omitted, since that is where
 * receiving inspection finds defects.
 */
public record FlagRequest(
        @NotNull Long modelId,
        @NotNull Long branchId,
        String stageCode,
        @NotNull @Min(1) Integer quantity,
        @Size(max = 512) String reason,
        @NotNull @PastOrPresent LocalDate eventDate) {
}
