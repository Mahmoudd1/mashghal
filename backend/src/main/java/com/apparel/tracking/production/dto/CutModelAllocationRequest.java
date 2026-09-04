package com.apparel.tracking.production.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CutModelAllocationRequest(
        @NotNull Long modelId,
        @NotNull Long branchId,
        @NotNull @Min(1) Integer quantityAllocated,
        @Size(max = 512) String note) {
}
