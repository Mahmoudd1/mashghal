package com.apparel.tracking.size.dto;

import com.apparel.tracking.size.domain.GarmentSize;

public record GarmentSizeDto(
        Long id, Long categoryId, String categoryNameAr, String code, String nameAr, int sortOrder, boolean active) {

    public static GarmentSizeDto from(GarmentSize size) {
        return new GarmentSizeDto(
                size.getId(),
                size.getCategory().getId(),
                size.getCategory().getNameAr(),
                size.getCode(),
                size.getNameAr(),
                size.getSortOrder(),
                size.isActive());
    }
}
