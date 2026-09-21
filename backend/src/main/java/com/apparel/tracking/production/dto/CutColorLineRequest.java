package com.apparel.tracking.production.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * One colour of a run cut by colour.
 *
 * @param weight      fabric of this colour off the shelf, its share of the عجز included
 * @param totalRolls  rolls that weight came off; none on a derby line that was not counted
 * @param reusedRolls of those, how many an earlier run had already opened
 */
public record CutColorLineRequest(
        @NotNull Long fabricColorId,
        @NotNull @DecimalMin("0.001") @Digits(integer = 11, fraction = 3) BigDecimal weight,
        @Min(0) Integer totalRolls,
        @Min(0) Integer reusedRolls) {

    public int rollsOrZero() {
        return totalRolls == null ? 0 : totalRolls;
    }

    public int reusedOrZero() {
        return reusedRolls == null ? 0 : reusedRolls;
    }
}
