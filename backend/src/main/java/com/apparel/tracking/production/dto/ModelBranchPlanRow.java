package com.apparel.tracking.production.dto;

/** Planned pieces for one model at one branch, summed across every cut feeding it. */
public record ModelBranchPlanRow(
        Long modelId, Long branchId, String branchCode, String branchNameAr, String branchNameEn, Long plannedQuantity) {
}
