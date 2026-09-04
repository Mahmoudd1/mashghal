package com.apparel.tracking.pipeline.dto;

import com.apparel.tracking.pipeline.domain.ModelBranchStageCount;

public record StageCountDto(
        Long stageId,
        String stageCode,
        String stageNameAr,
        String stageNameEn,
        int sequenceNo,
        int pieceCount,
        int flaggedCount) {

    public static StageCountDto from(ModelBranchStageCount count) {
        return new StageCountDto(
                count.getStage().getId(),
                count.getStage().getCode(),
                count.getStage().getNameAr(),
                count.getStage().getNameEn(),
                count.getStage().getSequenceNo(),
                count.getPieceCount(),
                count.getFlaggedCount());
    }
}
