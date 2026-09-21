package com.apparel.tracking.production.dto;

import java.math.BigDecimal;

import com.apparel.tracking.production.domain.CutColorLine;

/** One colour of a run cut by colour, as it was written up. */
public record CutColorLineDto(
        Long id,
        Long fabricColorId,
        String colorNameAr,
        BigDecimal weight,
        int totalRolls,
        int reusedRolls) {

    public static CutColorLineDto from(CutColorLine line) {
        return new CutColorLineDto(
                line.getId(),
                line.getColor().getId(),
                line.getColor().getNameAr(),
                line.getWeight(),
                line.getTotalRolls(),
                line.getReusedRolls());
    }
}
