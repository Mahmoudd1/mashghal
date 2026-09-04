package com.apparel.tracking.fabric.dto;

import com.apparel.tracking.fabric.domain.FabricColor;

public record FabricColorDto(Long id, Long fabricTypeId, String nameAr, String nameEn, boolean active) {

    public static FabricColorDto from(FabricColor color) {
        return new FabricColorDto(
                color.getId(),
                color.getFabricType().getId(),
                color.getNameAr(),
                color.getNameEn(),
                color.isActive());
    }
}
