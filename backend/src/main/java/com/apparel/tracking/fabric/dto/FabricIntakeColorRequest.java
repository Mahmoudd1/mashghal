package com.apparel.tracking.fabric.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** @param quantity optional weight/length for this colour */
public record FabricIntakeColorRequest(
        @NotNull Long fabricColorId,
        @NotNull @Min(1) Integer rollCount,
        @DecimalMin("0.001") @Digits(integer = 11, fraction = 3) BigDecimal quantity) {
}
