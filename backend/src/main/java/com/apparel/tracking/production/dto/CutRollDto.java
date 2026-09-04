package com.apparel.tracking.production.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.apparel.tracking.fabric.domain.FabricUnit;
import com.apparel.tracking.production.domain.CutRoll;

public record CutRollDto(
        Long id,
        Long cutId,
        Long fabricRollId,
        String rollLabel,
        LocalDate intakeDate,
        Long fabricTypeId,
        String fabricTypeNameAr,
        FabricUnit unit,
        Long fabricColorId,
        String colorNameAr,
        int layers,
        BigDecimal weightAtStart,
        BigDecimal weightConsumed,
        BigDecimal remainingAfter,
        BigDecimal defectWeight,
        boolean done,
        boolean rollClosed,
        String note) {

    public static CutRollDto from(CutRoll cutRoll) {
        var roll = cutRoll.getFabricRoll();
        var intake = roll.getIntake();
        return new CutRollDto(
                cutRoll.getId(),
                cutRoll.getCut().getId(),
                roll.getId(),
                roll.getLabel(),
                intake.getIntakeDate(),
                intake.getFabricType().getId(),
                intake.getFabricType().getNameAr(),
                intake.getFabricType().getUnit(),
                roll.getColor() == null ? null : roll.getColor().getId(),
                roll.getColor() == null ? null : roll.getColor().getNameAr(),
                cutRoll.getLayers(),
                cutRoll.getWeightAtStart(),
                cutRoll.getWeightConsumed(),
                cutRoll.getRemainingAfter(),
                cutRoll.getDefectWeight(),
                cutRoll.isDone(),
                roll.isClosed(),
                cutRoll.getNote());
    }
}
