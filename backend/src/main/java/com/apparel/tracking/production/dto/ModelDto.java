package com.apparel.tracking.production.dto;

import java.util.List;

import com.apparel.tracking.production.domain.Model;

/**
 * @param plannedByBranch     planned pieces per branch, derived from allocations
 * @param plannedTotal        sum across branches
 * @param mainCutCount        how many distinct MAIN cuts feed this model
 * @param drawsFromMultipleMainCuts the rare case worth surfacing in the UI
 */
public record ModelDto(
        Long id,
        String modelNumber,
        String nameAr,
        String nameEn,
        String note,
        boolean active,
        Long sewingBranchId,
        String sewingBranchCode,
        String sewingBranchNameAr,
        List<BranchQuantityDto> plannedByBranch,
        long plannedTotal,
        long mainCutCount,
        boolean drawsFromMultipleMainCuts) {

    public static ModelDto of(Model model, List<BranchQuantityDto> plannedByBranch, long mainCutCount) {
        long total = plannedByBranch.stream().mapToLong(BranchQuantityDto::quantity).sum();
        return new ModelDto(
                model.getId(),
                model.getModelNumber(),
                model.getNameAr(),
                model.getNameEn(),
                model.getNote(),
                model.isActive(),
                model.getSewingBranch() == null ? null : model.getSewingBranch().getId(),
                model.getSewingBranch() == null ? null : model.getSewingBranch().getCode(),
                model.getSewingBranch() == null ? null : model.getSewingBranch().getNameAr(),
                plannedByBranch,
                total,
                mainCutCount,
                mainCutCount > 1);
    }
}
