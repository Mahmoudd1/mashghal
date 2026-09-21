package com.apparel.tracking.production.dto;

import com.apparel.tracking.production.domain.ModelRole;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.apparel.tracking.production.domain.CutEntryMode;
import com.apparel.tracking.production.domain.CutType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

/**
 * @param parentMainCutId required for SECONDARY and DERBY, rejected for MAIN
 * @param branchId        where the cutting run physically happened
 * @param entryMode       DETAILED builds the cut from its rolls; SUMMARY carries the
 *                        totals below and draws the fabric oldest-batch-first
 * @param totalWeight     fabric off the shelf, the عجز included
 * @param wasteWeight     the عجز: the part of that weight binned rather than cut
 * @param reusedRolls     how many of {@code totalRolls} were already open, and so
 *                        were taken off a batch by an earlier cut
 * @param colorLines      the colours this run was cut in, one line each, every one
 *                        drawn down its own colour's batches oldest first. The
 *                        run's weight and rolls are then the sum of the lines, and
 *                        the totals above are ignored. Required for a DERBY run,
 *                        which is bought and asked for by colour; optional for a
 *                        SECONDARY one, which otherwise spends the fabric its main
 *                        cut already took
 */
public record CutRequest(
        @NotBlank @Size(max = 64) String cutNumber,
        @NotNull CutType cutType,
        Long parentMainCutId,
        @NotNull Long branchId,
        Long fabricTypeId,
        // The model this cut is for. A number that does not exist yet is created,
        // because opening a cut is normally the moment a model comes into being.
        // Ignored for a SECONDARY or DERBY run: it is cut out of a main run, so its
        // model, marker, description and label are inherited from the cut it names
        // as its parent.
        @Size(max = 64) String modelNumber,
        @Size(max = 128) String modelNameAr,
        Long modelSewingBranchId,
        // The suit this cut's model is half of, and which half. A suit number
        // that does not exist yet is created, like the model itself.
        @Size(max = 64) String suitModelNumber,
        ModelRole role,
        @NotNull @PastOrPresent LocalDate cutDate,
        @DecimalMin("0.001") @Digits(integer = 9, fraction = 3) BigDecimal cutLength,
        @Size(max = 512) String modelDescription,
        @Size(max = 128) String labelAr,
        @Size(max = 128) String labelEn,
        @Size(max = 512) String note,
        CutEntryMode entryMode,
        @Min(1) Integer totalRolls,
        @Min(0) Integer reusedRolls,
        @DecimalMin("0.001") @Digits(integer = 11, fraction = 3) BigDecimal totalWeight,
        @DecimalMin("0.0") @Digits(integer = 11, fraction = 3) BigDecimal wasteWeight,
        @Min(1) Integer totalLayers,
        @Valid List<CutColorLineRequest> colorLines) {

    /** Never null, so a caller that sends none reads the same as one that sends []. */
    public List<CutColorLineRequest> colorLinesOrEmpty() {
        return colorLines == null ? List.of() : colorLines;
    }

    /** DETAILED unless the request says otherwise, so existing callers are unchanged. */
    public CutEntryMode entryModeOrDefault() {
        return entryMode == null ? CutEntryMode.DETAILED : entryMode;
    }
}
