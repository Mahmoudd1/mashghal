package com.apparel.tracking.report.dto;

import java.util.List;

/** Everything, everywhere: totals across all models and branches. */
public record OverviewDto(
        long plannedTotal,
        long totalInPipeline,
        long flaggedTotal,
        boolean reconciled,
        long modelCount,
        List<StageTotalDto> stages,
        List<BranchRollupDto> branches) {
}
