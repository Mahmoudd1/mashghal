package com.apparel.tracking.size.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SizeCategoryRequest(
        @NotBlank @Size(max = 32) String code,
        @NotBlank @Size(max = 128) String nameAr,
        @Size(max = 128) String nameEn,
        @Size(max = 512) String note,
        Integer sortOrder,
        Boolean active) {
}
