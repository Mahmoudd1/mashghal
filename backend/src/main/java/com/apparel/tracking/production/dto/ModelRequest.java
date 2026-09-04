package com.apparel.tracking.production.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ModelRequest(
        @NotBlank @Size(max = 64) String modelNumber,
        @NotBlank @Size(max = 128) String nameAr,
        @Size(max = 128) String nameEn,
        @Size(max = 512) String note,
        Long sewingBranchId,
        Boolean active) {
}
