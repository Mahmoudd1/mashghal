package com.apparel.tracking.production.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

import com.apparel.tracking.production.domain.Cut;
import com.apparel.tracking.production.domain.CutEntryMode;
import com.apparel.tracking.production.domain.CutStatus;
import com.apparel.tracking.production.domain.CutType;

/**
 * @param totalLayers      layers laid out: summed from the rolls, or stated outright
 *                         on a cut written up from its totals
 * @param defectPercentage spoilage as a share of the fabric consumed, to two places
 * @param totalWasteWeight the عجز — fabric binned rather than cut, however recorded
 * @param weightPerPiece   fabric consumed per piece produced — the costing figure
 * @param fabricIntakeId   the batch this run named as the one it was cut from, with
 *                         {@code fabricColorId} the colour of it. Null when the
 *                         fabric was drawn oldest-batch-first instead
 * @param fabricDraws      which batches a summary cut drew on; empty for a detailed one
 * @param childWeight      what this cut's secondary runs have spent of its weight.
 *                         Their fabric came off its own rolls, so it is charged here
 *                         rather than taken from the batches again. Detail view only
 */
public record CutDto(
        Long id,
        String cutNumber,
        CutType cutType,
        Long parentMainCutId,
        String parentMainCutNumber,
        Long branchId,
        String branchCode,
        String branchNameAr,
        String branchNameEn,
        Long fabricTypeId,
        String fabricTypeNameAr,
        Long primaryModelId,
        String primaryModelNumber,
        String primaryModelNameAr,
        CutStatus status,
        LocalDate cutDate,
        BigDecimal cutLength,
        String modelDescription,
        String labelAr,
        String labelEn,
        String note,
        int totalLayers,
        BigDecimal totalWeightConsumed,
        BigDecimal totalDefectWeight,
        BigDecimal defectPercentage,
        BigDecimal totalWasteWeight,
        BigDecimal childWeight,
        CutEntryMode entryMode,
        Integer totalRolls,
        Integer reusedRolls,
        Long fabricIntakeId,
        LocalDate fabricIntakeDate,
        Long fabricColorId,
        String fabricColorNameAr,
        long derivedPieces,
        long totalAllocatedPieces,
        BigDecimal weightPerPiece,
        List<CutModelDerivedDto> modelTotals,
        List<CutModelAllocationDto> modelAllocations,
        List<CutModelSizeDto> sizeBreakdown,
        List<CutRollDto> rolls,
        List<CutFabricDrawDto> fabricDraws) {

    /** Header only — for list views, where the detail lists would be extra queries. */
    public static CutDto summary(
            Cut cut, int totalLayers, BigDecimal consumed, BigDecimal defect,
            BigDecimal waste, long allocated) {
        // Children are a detail-view figure: totalling them for a whole page
        // would be a query per row.
        return build(cut, totalLayers, consumed, defect, waste, BigDecimal.ZERO, 0L, allocated,
                List.of(), List.of(), List.of(), List.of(), List.of());
    }

    public static CutDto detail(
            Cut cut,
            int totalLayers,
            BigDecimal consumed,
            BigDecimal defect,
            BigDecimal waste,
            BigDecimal childWeight,
            List<CutModelDerivedDto> modelTotals,
            List<CutModelAllocationDto> modelAllocations,
            List<CutModelSizeDto> sizeBreakdown,
            List<CutRollDto> rolls,
            List<CutFabricDrawDto> fabricDraws) {
        long derived = modelTotals.stream().mapToLong(CutModelDerivedDto::derivedPieces).sum();
        long allocated = modelAllocations.stream().mapToLong(CutModelAllocationDto::quantityAllocated).sum();
        return build(cut, totalLayers, consumed, defect, waste, childWeight, derived, allocated,
                modelTotals, modelAllocations, sizeBreakdown, rolls, fabricDraws);
    }

    private static CutDto build(
            Cut cut,
            int totalLayers,
            BigDecimal consumed,
            BigDecimal defect,
            BigDecimal waste,
            BigDecimal childWeight,
            long derivedPieces,
            long allocatedPieces,
            List<CutModelDerivedDto> modelTotals,
            List<CutModelAllocationDto> modelAllocations,
            List<CutModelSizeDto> sizeBreakdown,
            List<CutRollDto> rolls,
            List<CutFabricDrawDto> fabricDraws) {

        BigDecimal consumedWeight = consumed == null ? BigDecimal.ZERO : consumed;
        BigDecimal defectWeight = defect == null ? BigDecimal.ZERO : defect;

        // Waste as a share of what went through the cut. Undefined with no fabric.
        BigDecimal defectPercentage = consumedWeight.signum() == 0
                ? BigDecimal.ZERO
                : defectWeight.multiply(BigDecimal.valueOf(100)).divide(consumedWeight, 2, RoundingMode.HALF_UP);

        // Costing: fabric per piece. Derived pieces where the marker gives them,
        // otherwise what was allocated, so older cuts still report something.
        long pieces = derivedPieces > 0 ? derivedPieces : allocatedPieces;
        BigDecimal weightPerPiece = pieces == 0
                ? BigDecimal.ZERO
                : consumedWeight.divide(BigDecimal.valueOf(pieces), 4, RoundingMode.HALF_UP);

        return new CutDto(
                cut.getId(),
                cut.getCutNumber(),
                cut.getCutType(),
                cut.getParentMainCut() == null ? null : cut.getParentMainCut().getId(),
                cut.getParentMainCut() == null ? null : cut.getParentMainCut().getCutNumber(),
                cut.getBranch().getId(),
                cut.getBranch().getCode(),
                cut.getBranch().getNameAr(),
                cut.getBranch().getNameEn(),
                cut.getFabricType() == null ? null : cut.getFabricType().getId(),
                cut.getFabricType() == null ? null : cut.getFabricType().getNameAr(),
                cut.getPrimaryModel() == null ? null : cut.getPrimaryModel().getId(),
                cut.getPrimaryModel() == null ? null : cut.getPrimaryModel().getModelNumber(),
                cut.getPrimaryModel() == null ? null : cut.getPrimaryModel().getNameAr(),
                cut.getStatus(),
                cut.getCutDate(),
                cut.getCutLength(),
                cut.getModelDescription(),
                cut.getLabelAr(),
                cut.getLabelEn(),
                cut.getNote(),
                totalLayers,
                consumedWeight,
                defectWeight,
                defectPercentage,
                waste == null ? BigDecimal.ZERO : waste,
                childWeight == null ? BigDecimal.ZERO : childWeight,
                cut.getEntryMode(),
                cut.getTotalRolls(),
                cut.getReusedRolls(),
                cut.getFabricIntake() == null ? null : cut.getFabricIntake().getId(),
                cut.getFabricIntake() == null ? null : cut.getFabricIntake().getIntakeDate(),
                cut.getFabricColor() == null ? null : cut.getFabricColor().getId(),
                cut.getFabricColor() == null ? null : cut.getFabricColor().getNameAr(),
                derivedPieces,
                allocatedPieces,
                weightPerPiece,
                modelTotals,
                modelAllocations,
                sizeBreakdown,
                rolls,
                fabricDraws);
    }
}
