package com.apparel.tracking.production.dto;

import com.apparel.tracking.production.domain.ModelRole;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param suitModelId the suit this model is half of, or null for a plain model
 * @param role        which half — required with a suit, rejected without one
 */
public record ModelRequest(
        @NotBlank @Size(max = 64) String modelNumber,
        @NotBlank @Size(max = 128) String nameAr,
        @Size(max = 128) String nameEn,
        @Size(max = 512) String note,
        Long sewingBranchId,
        Long suitModelId,
        ModelRole role,
        Boolean active) {
}
