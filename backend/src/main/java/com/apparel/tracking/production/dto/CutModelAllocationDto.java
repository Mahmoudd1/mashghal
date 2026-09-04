package com.apparel.tracking.production.dto;

import com.apparel.tracking.production.domain.CutModelAllocation;
import com.apparel.tracking.production.domain.CutType;

/** One allocation row, carrying enough of both sides to render either direction. */
public record CutModelAllocationDto(
        Long id,
        Long cutId,
        String cutNumber,
        CutType cutType,
        Long modelId,
        String modelNumber,
        String modelNameAr,
        String modelNameEn,
        Long branchId,
        String branchCode,
        String branchNameAr,
        String branchNameEn,
        int quantityAllocated,
        String note) {

    public static CutModelAllocationDto from(CutModelAllocation allocation) {
        return new CutModelAllocationDto(
                allocation.getId(),
                allocation.getCut().getId(),
                allocation.getCut().getCutNumber(),
                allocation.getCut().getCutType(),
                allocation.getModel().getId(),
                allocation.getModel().getModelNumber(),
                allocation.getModel().getNameAr(),
                allocation.getModel().getNameEn(),
                allocation.getBranch().getId(),
                allocation.getBranch().getCode(),
                allocation.getBranch().getNameAr(),
                allocation.getBranch().getNameEn(),
                allocation.getQuantityAllocated(),
                allocation.getNote());
    }
}
