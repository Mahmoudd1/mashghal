package com.apparel.tracking.reference.dto;

import com.apparel.tracking.reference.domain.PipelineStage;

public record PipelineStageDto(
        Long id, String code, String nameAr, String nameEn, int sequenceNo, boolean terminal, boolean active) {

    public static PipelineStageDto from(PipelineStage stage) {
        return new PipelineStageDto(
                stage.getId(),
                stage.getCode(),
                stage.getNameAr(),
                stage.getNameEn(),
                stage.getSequenceNo(),
                stage.isTerminal(),
                stage.isActive());
    }
}
