package com.apparel.tracking.production.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.apparel.tracking.production.domain.CutFabricDraw;

/**
 * One batch's share of a summary cut.
 *
 * @param id null on a preview, which is worked out but not yet written down
 */
public record CutFabricDrawDto(
        Long id,
        Long fabricIntakeId,
        LocalDate intakeDate,
        String supplierNameAr,
        BigDecimal weightConsumed,
        BigDecimal wasteWeight,
        int rollCount) {

    public static CutFabricDrawDto from(CutFabricDraw draw) {
        var intake = draw.getIntake();
        return new CutFabricDrawDto(
                draw.getId(),
                intake.getId(),
                intake.getIntakeDate(),
                intake.getSupplier() == null ? null : intake.getSupplier().getNameAr(),
                draw.getWeightConsumed(),
                draw.getWasteWeight(),
                draw.getRollCount());
    }
}
