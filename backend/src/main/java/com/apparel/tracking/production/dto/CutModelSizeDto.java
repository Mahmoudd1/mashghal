package com.apparel.tracking.production.dto;

import com.apparel.tracking.production.domain.CutModelSize;
import com.apparel.tracking.reference.domain.Branch;

/**
 * @param totalPieces layers across the whole cut multiplied by {@code piecesPerLayer}
 */
public record CutModelSizeDto(
        Long id,
        Long modelId,
        String modelNumber,
        Long sizeId,
        String sizeCode,
        Long categoryId,
        String categoryNameAr,
        int piecesPerLayer,
        long totalPieces,
        Long branchId,
        String branchCode,
        String branchNameAr,
        /** True when this size names its own branch rather than inheriting the model's. */
        boolean branchOverridden) {

    /** @param effectiveBranch where this size is actually sewn, after inheritance */
    public static CutModelSizeDto from(CutModelSize row, int totalLayers, Branch effectiveBranch) {
        var size = row.getSize();
        return new CutModelSizeDto(
                row.getId(),
                row.getModel().getId(),
                row.getModel().getModelNumber(),
                size.getId(),
                size.getCode(),
                size.getCategory().getId(),
                size.getCategory().getNameAr(),
                row.getPiecesPerLayer(),
                (long) totalLayers * row.getPiecesPerLayer(),
                effectiveBranch == null ? null : effectiveBranch.getId(),
                effectiveBranch == null ? null : effectiveBranch.getCode(),
                effectiveBranch == null ? null : effectiveBranch.getNameAr(),
                row.getBranch() != null);
    }
}
