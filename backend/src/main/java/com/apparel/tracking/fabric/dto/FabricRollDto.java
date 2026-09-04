package com.apparel.tracking.fabric.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.apparel.tracking.fabric.domain.FabricPool;
import com.apparel.tracking.fabric.domain.FabricRoll;
import com.apparel.tracking.fabric.domain.FabricUnit;

public record FabricRollDto(
        Long id,
        Long fabricIntakeId,
        LocalDate intakeDate,
        Long fabricTypeId,
        String fabricTypeNameAr,
        FabricUnit unit,
        FabricPool pool,
        Long fabricColorId,
        String colorNameAr,
        String label,
        BigDecimal initialWeight,
        BigDecimal remainingWeight,
        BigDecimal consumedWeight,
        boolean closed) {

    public static FabricRollDto from(FabricRoll roll) {
        var intake = roll.getIntake();
        return new FabricRollDto(
                roll.getId(),
                intake.getId(),
                intake.getIntakeDate(),
                intake.getFabricType().getId(),
                intake.getFabricType().getNameAr(),
                intake.getFabricType().getUnit(),
                intake.isDerbyPool() ? FabricPool.DERBY : FabricPool.REGULAR,
                roll.getColor() == null ? null : roll.getColor().getId(),
                roll.getColor() == null ? null : roll.getColor().getNameAr(),
                roll.getLabel(),
                roll.getInitialWeight(),
                roll.getRemainingWeight(),
                roll.consumedWeight(),
                roll.isClosed());
    }
}
