package com.apparel.tracking.production.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.apparel.tracking.production.domain.CutType;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

/**
 * @param parentMainCutId required for SECONDARY and DERBY, rejected for MAIN
 * @param branchId        where the cutting run physically happened
 */
public record CutRequest(
        @NotBlank @Size(max = 64) String cutNumber,
        @NotNull CutType cutType,
        Long parentMainCutId,
        @NotNull Long branchId,
        Long fabricTypeId,
        // The model this cut is for. A number that does not exist yet is created,
        // because opening a cut is normally the moment a model comes into being.
        @Size(max = 64) String modelNumber,
        @Size(max = 128) String modelNameAr,
        Long modelSewingBranchId,
        @NotNull @PastOrPresent LocalDate cutDate,
        @DecimalMin("0.001") @Digits(integer = 9, fraction = 3) BigDecimal cutLength,
        @Size(max = 512) String modelDescription,
        @Size(max = 128) String labelAr,
        @Size(max = 128) String labelEn,
        @Size(max = 512) String note) {
}
