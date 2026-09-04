package com.apparel.tracking.fabric.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

/**
 * One purchase. No colour here on purpose — the breakdown is added later.
 *
 * @param derbyPool     true to top up the fabric type's derby instead of its regular stock
 * @param pricePerUnit  optional — whoever records the purchase often does not know
 *                      what was paid, and the owner fills it in later
 */
public record FabricIntakeRequest(
        @NotNull Long fabricTypeId,
        boolean derbyPool,
        Long supplierId,
        @NotNull @PastOrPresent LocalDate intakeDate,
        @NotNull @Min(1) Integer totalRolls,
        @NotNull @DecimalMin("0.001") @Digits(integer = 11, fraction = 3) BigDecimal totalQuantity,
        @DecimalMin("0.0") @Digits(integer = 9, fraction = 3) BigDecimal pricePerUnit,
        @Size(max = 512) String note) {
}
