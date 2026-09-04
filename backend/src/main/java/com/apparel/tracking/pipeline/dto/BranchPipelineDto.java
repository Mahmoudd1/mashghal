package com.apparel.tracking.pipeline.dto;

import java.util.List;

/**
 * One model's pipeline at one branch.
 *
 * @param plannedQuantity pieces allocated to this model at this branch
 * @param totalInPipeline pieces counted across all stages — must equal plannedQuantity
 * @param reconciled      whether the invariant currently holds
 */
public record BranchPipelineDto(
        Long branchId,
        String branchCode,
        String branchNameAr,
        String branchNameEn,
        long plannedQuantity,
        long totalInPipeline,
        long flaggedTotal,
        boolean reconciled,
        List<StageCountDto> stages) {
}
