package com.apparel.tracking.pipeline.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

/** A move between two named stages, for corrections and non-receiving progress. */
public record StageMoveRequest(
        @NotNull Long modelId,
        @NotNull Long branchId,
        @NotBlank String fromStageCode,
        @NotBlank String toStageCode,
        @NotNull @Min(1) Integer quantity,
        @NotNull @PastOrPresent LocalDate movementDate,
        @Size(max = 512) String note) {
}
