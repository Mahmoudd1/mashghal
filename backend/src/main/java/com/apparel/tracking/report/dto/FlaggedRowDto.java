package com.apparel.tracking.report.dto;

/** Flagged pieces broken down to a single model + branch + stage. */
public record FlaggedRowDto(
        Long modelId,
        String modelNumber,
        String modelNameAr,
        String modelNameEn,
        Long branchId,
        String branchCode,
        String branchNameAr,
        String branchNameEn,
        Long stageId,
        String stageCode,
        long flaggedCount,
        long pieceCount) {
}
