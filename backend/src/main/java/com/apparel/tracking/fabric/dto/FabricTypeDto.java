package com.apparel.tracking.fabric.dto;

import java.util.List;

import com.apparel.tracking.fabric.domain.FabricType;
import com.apparel.tracking.fabric.domain.FabricUnit;

/** @param hasDerby whether this type has a derby pool; at most one, ever */
public record FabricTypeDto(
        Long id,
        String nameAr,
        String nameEn,
        FabricUnit unit,
        boolean active,
        boolean hasDerby,
        List<FabricColorDto> colors) {

    public static FabricTypeDto from(FabricType type, boolean hasDerby) {
        return new FabricTypeDto(
                type.getId(),
                type.getNameAr(),
                type.getNameEn(),
                type.getUnit(),
                type.isActive(),
                hasDerby,
                type.getColors().stream()
                        .sorted((a, b) -> a.getNameAr().compareTo(b.getNameAr()))
                        .map(FabricColorDto::from)
                        .toList());
    }
}
