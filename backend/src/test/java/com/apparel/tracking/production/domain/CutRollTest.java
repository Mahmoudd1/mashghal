package com.apparel.tracking.production.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import com.apparel.tracking.common.exception.BusinessRuleException;

import org.junit.jupiter.api.Test;

/**
 * What happens to the fabric a cut did not use.
 *
 * <p>Finishing a roll does not mean the cut used every gram of it: the balance
 * goes in the bin with the roll, and has to be recorded as waste rather than
 * quietly counted as consumption.
 */
class CutRollTest {

    private static CutRoll line() {
        return new CutRoll();
    }

    @Test
    void keepsTheLeftoverOnTheRollWhileItStaysOpen() {
        CutRoll line = line();
        line.applyWeights(new BigDecimal("50.000"), new BigDecimal("45.000"), false);

        assertThat(line.getWeightConsumed()).isEqualByComparingTo("45.000");
        assertThat(line.getRemainingAfter()).isEqualByComparingTo("5.000");
        assertThat(line.getWasteWeight()).isEqualByComparingTo("0");
        assertThat(line.isDone()).isFalse();
    }

    @Test
    void wastesTheLeftoverWhenTheRunFinishesTheRoll() {
        CutRoll line = line();
        line.applyWeights(new BigDecimal("50.000"), new BigDecimal("45.000"), true);

        assertThat(line.getWeightConsumed()).isEqualByComparingTo("45.000");
        assertThat(line.getWasteWeight()).isEqualByComparingTo("5.000");
        // The roll is gone, so nothing is left on it to pick up later.
        assertThat(line.getRemainingAfter()).isEqualByComparingTo("0");
        assertThat(line.isDone()).isTrue();
    }

    @Test
    void takesAFinishedRollWithNoFigureAsFullyUsed() {
        // The old behaviour, and still right when nobody weighed a leftover.
        CutRoll line = line();
        line.applyWeights(new BigDecimal("50.000"), null, true);

        assertThat(line.getWeightConsumed()).isEqualByComparingTo("50.000");
        assertThat(line.getWasteWeight()).isEqualByComparingTo("0");
    }

    @Test
    void countsEverythingOffTheRollWhetherCutOrBinned() {
        CutRoll line = line();
        line.applyWeights(new BigDecimal("50.000"), new BigDecimal("45.000"), true);

        assertThat(line.weightOffTheRoll()).isEqualByComparingTo("50.000");
    }

    @Test
    void finishesTheRollWhenTheCutUsedEveryGram() {
        CutRoll line = line();
        line.applyWeights(new BigDecimal("50.000"), new BigDecimal("50.000"), false);

        assertThat(line.isDone()).isTrue();
        assertThat(line.getWasteWeight()).isEqualByComparingTo("0");
    }

    @Test
    void refusesToUseMoreThanTheRollHeld() {
        assertThatThrownBy(() -> line().applyWeights(
                new BigDecimal("50.000"), new BigDecimal("51.000"), true))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("cannot have used");
    }

    @Test
    void refusesAnOpenRollWithNoUsedWeight() {
        assertThatThrownBy(() -> line().applyWeights(new BigDecimal("50.000"), null, false))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("how much of the roll");
    }

    @Test
    void balancesTheColumnsTheDatabaseChecks() {
        // cut_roll_weight_balances: consumed + waste = start - remainingAfter.
        CutRoll line = line();
        line.applyWeights(new BigDecimal("50.000"), new BigDecimal("45.000"), true);

        assertThat(line.getWeightConsumed().add(line.getWasteWeight()))
                .isEqualByComparingTo(line.getWeightAtStart().subtract(line.getRemainingAfter()));
    }
}
