package com.apparel.tracking.fabric.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.apparel.tracking.common.exception.BusinessRuleException;

import org.junit.jupiter.api.Test;

/**
 * The batch's own arithmetic: when it counts as finished, and what the fabric
 * that never reached a cut adds up to. No database — these are subtractions.
 */
class FabricIntakeTest {

    /** A batch of {@code rolls} rolls holding {@code quantity} in total. */
    private static FabricIntake batch(int rolls, String quantity) {
        FabricIntake intake = new FabricIntake();
        intake.setIntakeDate(LocalDate.of(2026, 8, 26));
        intake.setTotalRolls(rolls);
        intake.setTotalQuantity(new BigDecimal(quantity));
        intake.setConsumedQuantity(BigDecimal.ZERO);
        return intake;
    }

    /** Cuts draw weight off the batch; a roll is counted only when it is finished. */
    private static void cut(FabricIntake intake, String quantity, boolean finishesRoll) {
        intake.consumeWeight(new BigDecimal(quantity));
        if (finishesRoll) {
            intake.consumeRoll();
        }
    }

    /** A roll finished with fabric still on it: what the cut used, and what it binned. */
    private static void cutAndBin(FabricIntake intake, String used, String wasted) {
        intake.consumeWeight(new BigDecimal(used));
        intake.wasteWeight(new BigDecimal(wasted));
        intake.consumeRoll();
    }

    @Test
    void isNotFinishedWhileRollsRemain() {
        FabricIntake intake = batch(3, "300.000");
        cut(intake, "95.000", true);

        assertThat(intake.isFinished()).isFalse();
        assertThat(intake.remainingRolls()).isEqualTo(2);
    }

    @Test
    void isFinishedOnceTheLastRollComesOff() {
        FabricIntake intake = batch(2, "200.000");
        cut(intake, "100.000", true);
        cut(intake, "100.000", true);

        assertThat(intake.isFinished()).isTrue();
        assertThat(intake.remainingRolls()).isZero();
    }

    @Test
    void countsTheWeightNoCutEverTookAsWaste() {
        // 300 bought, 285 cut, and the last roll closed on its offcut.
        FabricIntake intake = batch(3, "300.000");
        cut(intake, "95.000", true);
        cut(intake, "95.000", true);
        cut(intake, "95.000", true);

        assertThat(intake.isFinished()).isTrue();
        assertThat(intake.wasteQuantity()).isEqualByComparingTo("15.000");
        assertThat(intake.wastePercentage()).isEqualByComparingTo("5.00");
    }

    @Test
    void reportsNoWasteWhileTheBatchIsStillInUse() {
        // Two thirds of the weight is untouched, but it is stock, not loss.
        FabricIntake intake = batch(3, "300.000");
        cut(intake, "100.000", true);

        assertThat(intake.isFinished()).isFalse();
        assertThat(intake.wasteQuantity()).isEqualByComparingTo("0");
        assertThat(intake.wastePercentage()).isEqualByComparingTo("0");
    }

    @Test
    void reportsNoWasteWhenEveryGramWasCut() {
        FabricIntake intake = batch(2, "200.000");
        cut(intake, "120.000", true);
        cut(intake, "80.000", true);

        assertThat(intake.isFinished()).isTrue();
        assertThat(intake.wasteQuantity()).isEqualByComparingTo("0");
        assertThat(intake.wastePercentage()).isEqualByComparingTo("0.00");
    }

    @Test
    void roundsThePercentageToTwoPlaces() {
        // 10 of 300 is 3.333…%, which has to land somewhere.
        FabricIntake intake = batch(1, "300.000");
        cut(intake, "290.000", true);

        assertThat(intake.wastePercentage()).isEqualByComparingTo("3.33");
    }

    @Test
    void stopsBeingFinishedWhenACutIsUndone() {
        // Editing a cut releases the roll, and the batch is live stock again —
        // this is why finished is derived and not a stored flag.
        FabricIntake intake = batch(1, "100.000");
        cut(intake, "90.000", true);
        assertThat(intake.isFinished()).isTrue();
        assertThat(intake.wasteQuantity()).isEqualByComparingTo("10.000");

        intake.releaseRoll();
        intake.releaseWeight(new BigDecimal("90.000"));

        assertThat(intake.isFinished()).isFalse();
        assertThat(intake.wasteQuantity()).isEqualByComparingTo("0");
    }

    @Test
    void treatsAWhollyUncutBatchAsTotalWasteOnceItsRollsAreGone() {
        // Every roll closed on its full weight: nothing was ever cut from it.
        FabricIntake intake = batch(1, "50.000");
        intake.consumeRoll();

        assertThat(intake.isFinished()).isTrue();
        assertThat(intake.wasteQuantity()).isEqualByComparingTo("50.000");
        assertThat(intake.wastePercentage()).isEqualByComparingTo("100.00");
    }

    @Test
    void countsALeftoverAsWasteAsSoonAsTheRollIsBinned() {
        // One of three rolls done: 45 kg cut, 5 kg thrown away with the roll.
        FabricIntake intake = batch(3, "300.000");
        cutAndBin(intake, "45.000", "5.000");

        assertThat(intake.isFinished()).isFalse();
        // Waste is known now, without waiting for the batch to be used up.
        assertThat(intake.wasteQuantity()).isEqualByComparingTo("5.000");
        assertThat(intake.wastePercentage()).isEqualByComparingTo("1.67");
    }

    @Test
    void stopsCountingBinnedFabricAsAvailableStock() {
        FabricIntake intake = batch(3, "300.000");
        cutAndBin(intake, "45.000", "5.000");

        // 50 kg left the batch, so 250 remains — not 255.
        assertThat(intake.remainingQuantity()).isEqualByComparingTo("250.000");
    }

    @Test
    void keepsConsumedMeaningFabricThatBecameGarments() {
        FabricIntake intake = batch(3, "300.000");
        cutAndBin(intake, "45.000", "5.000");

        assertThat(intake.getConsumedQuantity()).isEqualByComparingTo("45.000");
        assertThat(intake.getWastedQuantity()).isEqualByComparingTo("5.000");
    }

    @Test
    void addsUnaccountedWeightToTheRecordedWasteOnceTheBatchIsDone() {
        // 300 bought. Rolls accounted for 285 cut and 5 binned; the last 10 was
        // never seen on any roll, and with no rolls left it cannot be stock.
        FabricIntake intake = batch(2, "300.000");
        cutAndBin(intake, "285.000", "5.000");
        intake.consumeRoll();

        assertThat(intake.isFinished()).isTrue();
        assertThat(intake.wasteQuantity()).isEqualByComparingTo("15.000");
        assertThat(intake.wastePercentage()).isEqualByComparingTo("5.00");
    }

    @Test
    void refusesToWasteMoreThanTheBatchHasLeft() {
        FabricIntake intake = batch(1, "100.000");
        cut(intake, "95.000", false);

        assertThatThrownBy(() -> intake.wasteWeight(new BigDecimal("10.000")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("cannot waste");
    }

    @Test
    void givesBinnedFabricBackWhenTheCutIsUndone() {
        FabricIntake intake = batch(3, "300.000");
        cutAndBin(intake, "45.000", "5.000");

        intake.releaseRoll();
        intake.releaseWeight(new BigDecimal("45.000"));
        intake.releaseWaste(new BigDecimal("5.000"));

        assertThat(intake.remainingQuantity()).isEqualByComparingTo("300.000");
        assertThat(intake.wasteQuantity()).isEqualByComparingTo("0");
    }
}
