package com.apparel.tracking.production.dto;

/**
 * What the marker says one model should yield from this cut, against what has
 * actually been distributed to branches.
 *
 * @param derivedPieces    layers x sum(pieces per layer) across this model's sizes
 * @param allocatedPieces  what the branch allocations currently add up to
 * @param balanced         whether the two agree; closing the cut requires it
 */
public record CutModelDerivedDto(
        Long modelId,
        String modelNumber,
        String modelNameAr,
        int piecesPerLayer,
        long derivedPieces,
        long allocatedPieces,
        long unallocatedPieces,
        boolean balanced) {
}
