package com.apparel.tracking.production.dto;

import java.util.List;

/**
 * Every cut feeding one model. {@code drawsFromMultipleMainCuts} is the rare
 * case: the UI surfaces it as a callout so data entry does not mistake it for a
 * duplicate-entry error.
 */
public record ModelCutsDto(
        Long modelId,
        String modelNumber,
        long mainCutCount,
        boolean drawsFromMultipleMainCuts,
        List<CutModelAllocationDto> allocations) {
}
