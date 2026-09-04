package com.apparel.tracking.size.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record GarmentSizeRequest(
        @NotNull Long sizeCategoryId,
        @NotBlank @Size(max = 32) String code,
        @Size(max = 64) String nameAr,
        Integer sortOrder,
        Boolean active) {
}
