package com.apparel.tracking.fabric.dto;

import java.math.BigDecimal;

/** Roll count per colour, for a fabric type or one intake date. */
public record ColorRollCountDto(Long colorId, String colorNameAr, long rollCount, BigDecimal remainingWeight) {

    public ColorRollCountDto {
        remainingWeight = remainingWeight == null ? BigDecimal.ZERO : remainingWeight;
    }
}
