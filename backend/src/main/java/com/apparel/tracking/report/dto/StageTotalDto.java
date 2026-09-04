package com.apparel.tracking.report.dto;

/** Pieces and flags in one stage, summed over whatever the report grouped by. */
public record StageTotalDto(
        Long stageId, String stageCode, String stageNameAr, String stageNameEn, int sequenceNo,
        long pieceCount, long flaggedCount) {

    public StageTotalDto {
        pieceCount = pieceCount == 0 ? 0 : pieceCount;
    }
}
