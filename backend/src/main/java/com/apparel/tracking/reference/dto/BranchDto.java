package com.apparel.tracking.reference.dto;

import com.apparel.tracking.reference.domain.Branch;

public record BranchDto(Long id, String code, String nameAr, String nameEn, int sortOrder, boolean active) {

    public static BranchDto from(Branch branch) {
        return new BranchDto(
                branch.getId(),
                branch.getCode(),
                branch.getNameAr(),
                branch.getNameEn(),
                branch.getSortOrder(),
                branch.isActive());
    }
}
