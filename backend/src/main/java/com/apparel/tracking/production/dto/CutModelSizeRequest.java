package com.apparel.tracking.production.dto;

import com.apparel.tracking.production.domain.ModelRole;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** How many pieces of one size a single layer yields, for one model on this cut. */
/**
 * Identify the model either by {@code modelId} or by {@code modelNumber}. A model
 * number that does not exist yet is created — the cut is where a model first
 * appears in practice, so entry should not stall to go and register it.
 *
 * @param branchId which branch sews this size; null inherits the model's sewing
 *                 branch, so an unsplit model never sets it
 */
public record CutModelSizeRequest(
        Long modelId,
        @Size(max = 64) String modelNumber,
        @Size(max = 128) String modelNameAr,
        /**
         * The suit this model is half of, named by number. Given with {@code role},
         * a model created here is created as that half — which is how one cutting
         * run can lay out a suit's top and its bottom side by side, each as its
         * own model row with its own sizes.
         */
        @Size(max = 64) String suitModelNumber,
        ModelRole role,
        @NotNull Long garmentSizeId,
        @NotNull @Min(1) Integer piecesPerLayer,
        Long branchId) {
}
