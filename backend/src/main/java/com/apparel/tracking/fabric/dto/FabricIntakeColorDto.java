package com.apparel.tracking.fabric.dto;

import java.math.BigDecimal;

import com.apparel.tracking.fabric.domain.FabricIntakeColor;

public record FabricIntakeColorDto(
        Long id, Long colorId, String colorNameAr, String colorNameEn, int rollCount, BigDecimal quantity) {

    public static FabricIntakeColorDto from(FabricIntakeColor row) {
        return new FabricIntakeColorDto(
                row.getId(),
                row.getColor().getId(),
                row.getColor().getNameAr(),
                row.getColor().getNameEn(),
                row.getRollCount(),
                row.getQuantity());
    }
}
