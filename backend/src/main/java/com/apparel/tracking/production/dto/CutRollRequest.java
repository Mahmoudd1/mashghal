package com.apparel.tracking.production.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Adds a roll to a cut.
 *
 * <p>Exactly one of {@code fabricRollId} / {@code fabricIntakeId} identifies where
 * the fabric comes from: an existing part-used roll, or a fresh roll drawn off a
 * dated batch. A fresh roll needs its weighed {@code initialWeight}; a continuing
 * one already knows what it holds.
 *
 * @param weightUsed how much of the roll this cut consumed. Given while finishing
 *                   the roll, whatever is left over is thrown away with it and
 *                   recorded as waste; omitted, the cut is taken to have used the
 *                   whole balance. The remainder is calculated, not entered.
 */
public record CutRollRequest(
        Long fabricRollId,
        Long fabricIntakeId,
        Long fabricColorId,
        @Size(max = 64) String rollLabel,
        @DecimalMin("0.001") @Digits(integer = 11, fraction = 3) BigDecimal initialWeight,
        @NotNull @Min(1) Integer layers,
        @DecimalMin("0.0") @Digits(integer = 11, fraction = 3) BigDecimal defectWeight,
        boolean done,
        @DecimalMin("0.001") @Digits(integer = 11, fraction = 3) BigDecimal weightUsed,
        @Size(max = 512) String note) {
}
