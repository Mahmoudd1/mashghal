package com.apparel.tracking.size.dto;

/** A model that has been cut in a given size category. */
public record ModelSizeCategoryRowDto(
        Long modelId,
        String modelNumber,
        String modelNameAr,
        Long categoryId,
        String categoryNameAr,
        long piecesPerLayer) {
}
