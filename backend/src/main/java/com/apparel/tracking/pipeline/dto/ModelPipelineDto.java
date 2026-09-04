package com.apparel.tracking.pipeline.dto;

import java.util.List;

/** A model's pipeline across every branch it is produced at. */
public record ModelPipelineDto(
        Long modelId,
        String modelNumber,
        String modelNameAr,
        String modelNameEn,
        long plannedTotal,
        long totalInPipeline,
        long flaggedTotal,
        boolean reconciled,
        List<BranchPipelineDto> branches) {
}
