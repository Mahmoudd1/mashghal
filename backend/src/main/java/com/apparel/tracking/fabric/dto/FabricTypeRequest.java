package com.apparel.tracking.fabric.dto;

import com.apparel.tracking.fabric.domain.FabricUnit;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record FabricTypeRequest(
        @NotBlank @Size(max = 128) String nameAr,
        @Size(max = 128) String nameEn,
        @NotNull FabricUnit unit,
        Boolean active) {
}
