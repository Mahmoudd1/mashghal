package com.apparel.tracking.fabric.dto;

import java.math.BigDecimal;

/** How many rolls are sitting part-used, per fabric type and colour. */
public record OpenRollRowDto(
        Long fabricTypeId,
        String fabricTypeNameAr,
        Long colorId,
        String colorNameAr,
        long openRolls,
        BigDecimal remainingWeight) {

    public OpenRollRowDto {
        remainingWeight = remainingWeight == null ? BigDecimal.ZERO : remainingWeight;
    }
}
