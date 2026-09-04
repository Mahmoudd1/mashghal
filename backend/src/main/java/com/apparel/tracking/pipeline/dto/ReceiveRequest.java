package com.apparel.tracking.pipeline.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

/**
 * "Received N pieces of model X from branch Y on date D."
 *
 * <p>No flagged count: defects are found during receiving inspection and
 * recorded afterwards against the RECEIVED stage, not carried in on the move.
 */
public record ReceiveRequest(
        @NotNull Long modelId,
        @NotNull Long branchId,
        @NotNull @Min(1) Integer quantity,
        @NotNull @PastOrPresent LocalDate receivedDate,
        @Size(max = 512) String note) {
}
