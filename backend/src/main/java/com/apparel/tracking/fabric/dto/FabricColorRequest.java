package com.apparel.tracking.fabric.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FabricColorRequest(
        @NotBlank @Size(max = 128) String nameAr,
        @Size(max = 128) String nameEn,
        Boolean active) {
}
