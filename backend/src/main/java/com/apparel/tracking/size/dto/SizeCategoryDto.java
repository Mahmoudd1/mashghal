package com.apparel.tracking.size.dto;

import java.util.List;

import com.apparel.tracking.size.domain.SizeCategory;

public record SizeCategoryDto(
        Long id,
        String code,
        String nameAr,
        String nameEn,
        String note,
        int sortOrder,
        boolean active,
        List<GarmentSizeDto> sizes) {

    public static SizeCategoryDto from(SizeCategory category) {
        return new SizeCategoryDto(
                category.getId(),
                category.getCode(),
                category.getNameAr(),
                category.getNameEn(),
                category.getNote(),
                category.getSortOrder(),
                category.isActive(),
                category.getSizes().stream().map(GarmentSizeDto::from).toList());
    }
}
