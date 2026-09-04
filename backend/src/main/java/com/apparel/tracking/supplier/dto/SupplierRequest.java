package com.apparel.tracking.supplier.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SupplierRequest(
        @NotBlank @Size(max = 128) String nameAr,
        @Size(max = 128) String nameEn,
        @Size(max = 64) String phone,
        @Size(max = 512) String note,
        Boolean active) {
}
