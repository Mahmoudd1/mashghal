package com.apparel.tracking.report.dto;

import java.util.List;

/**
 * Pipeline totals for one branch across every model made there.
 *
 * @param modelCount how many models have pieces at this branch
 */
public record BranchRollupDto(
        Long branchId,
        String branchCode,
        String branchNameAr,
        String branchNameEn,
        long plannedTotal,
        long totalInPipeline,
        long flaggedTotal,
        boolean reconciled,
        long modelCount,
        List<StageTotalDto> stages) {
}
