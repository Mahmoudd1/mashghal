package com.apparel.tracking.production.dto;

/** A quantity attributed to one branch. */
public record BranchQuantityDto(
        Long branchId, String branchCode, String branchNameAr, String branchNameEn, long quantity) {
}
