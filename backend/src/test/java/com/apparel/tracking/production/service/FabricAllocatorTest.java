package com.apparel.tracking.production.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.apparel.tracking.common.exception.BusinessRuleException;
import com.apparel.tracking.fabric.domain.FabricIntake;
import com.apparel.tracking.fabric.domain.FabricType;
import com.apparel.tracking.production.service.FabricAllocator.Share;

import org.junit.jupiter.api.Test;

/**
 * Emptying the store oldest first.
 *
 * <p>A summary cut names no batch, so the one it drew on is inferred. Getting
 * this wrong moves fabric off the wrong purchase, which shows up much later as a
 * batch that will not balance.
 */
class FabricAllocatorTest {

    /** A batch of {@code rolls} rolls holding {@code quantity}, with nothing drawn yet. */
    private static FabricIntake batch(long id, String date, int rolls, String quantity) {
        FabricIntake intake = new FabricIntake();
        intake.setId(id);
        intake.setFabricType(new FabricType());
        intake.setIntakeDate(LocalDate.parse(date));
        intake.setTotalRolls(rolls);
        intake.setTotalQuantity(new BigDecimal(quantity));
        intake.setConsumedQuantity(BigDecimal.ZERO);
        intake.setWastedQuantity(BigDecimal.ZERO);
        return intake;
    }

    private static List<Share> allocate(List<FabricIntake> batches, String weight, int rolls) {
        return FabricAllocator.allocate(batches, new BigDecimal(weight), rolls);
    }

    @Test
    void takesEverythingFromTheOldestBatchWhenItCanCoverIt() {
        FabricIntake july = batch(1, "2026-07-12", 50, "500.000");
        FabricIntake august = batch(2, "2026-08-26", 50, "500.000");

        List<Share> shares = allocate(List.of(july, august), "100.000", 10);

        assertThat(shares).hasSize(1);
        assertThat(shares.getFirst().intake()).isSameAs(july);
        assertThat(shares.getFirst().weight()).isEqualByComparingTo("100.000");
        assertThat(shares.getFirst().rolls()).isEqualTo(10);
    }

    @Test
    void spillsIntoTheNextBatchWhenTheOldestRunsOut() {
        FabricIntake july = batch(1, "2026-07-12", 10, "80.000");
        FabricIntake august = batch(2, "2026-08-26", 50, "500.000");

        List<Share> shares = allocate(List.of(july, august), "200.000", 25);

        assertThat(shares).hasSize(2);
        assertThat(shares.get(0).weight()).isEqualByComparingTo("80.000");
        assertThat(shares.get(0).rolls()).isEqualTo(10);
        assertThat(shares.get(1).weight()).isEqualByComparingTo("120.000");
        assertThat(shares.get(1).rolls()).isEqualTo(15);
    }

    @Test
    void keepsDrawingPastABatchThatStillHasWeightButNoRollsLeft() {
        // The two run out at different rates, so tying them together would
        // invent a shortage that is not there.
        FabricIntake july = batch(1, "2026-07-12", 2, "500.000");
        FabricIntake august = batch(2, "2026-08-26", 50, "500.000");

        List<Share> shares = allocate(List.of(july, august), "100.000", 20);

        assertThat(shares.get(0).weight()).isEqualByComparingTo("100.000");
        assertThat(shares.get(0).rolls()).isEqualTo(2);
        // Weight was satisfied by the first batch; the rolls were not.
        assertThat(shares.get(1).weight()).isEqualByComparingTo("0");
        assertThat(shares.get(1).rolls()).isEqualTo(18);
    }

    @Test
    void countsOnlyWhatABatchHasLeftRatherThanWhatItHeld() {
        FabricIntake july = batch(1, "2026-07-12", 50, "500.000");
        july.consumeWeight(new BigDecimal("460.000"));
        july.wasteWeight(new BigDecimal("20.000"));
        FabricIntake august = batch(2, "2026-08-26", 50, "500.000");

        List<Share> shares = allocate(List.of(july, august), "100.000", 5);

        // 500 - 460 cut - 20 binned leaves 20, not 40.
        assertThat(shares.get(0).weight()).isEqualByComparingTo("20.000");
        assertThat(shares.get(1).weight()).isEqualByComparingTo("80.000");
    }

    @Test
    void refusesADrawTheFabricCannotCover() {
        FabricIntake july = batch(1, "2026-07-12", 10, "80.000");

        assertThatThrownBy(() -> allocate(List.of(july), "200.000", 5))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("short of");
    }

    @Test
    void refusesMoreRollsThanTheFabricHas() {
        FabricIntake july = batch(1, "2026-07-12", 4, "500.000");

        assertThatThrownBy(() -> allocate(List.of(july), "100.000", 9))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("rolls short");
    }

    @Test
    void refusesToDrawNothing() {
        assertThatThrownBy(() -> allocate(List.of(batch(1, "2026-07-12", 5, "10.000")), "0", 1))
                .isInstanceOf(BusinessRuleException.class);
    }

    // --- splitting the عجز ---------------------------------------------------

    @Test
    void splitsWasteByEachBatchesShareOfTheCut() {
        List<Share> shares = List.of(
                new Share(batch(1, "2026-07-12", 10, "80.000"), new BigDecimal("80.000"), 10),
                new Share(batch(2, "2026-08-26", 50, "500.000"), new BigDecimal("120.000"), 15));

        List<BigDecimal> split = FabricAllocator.splitWaste(shares, new BigDecimal("10.000"));

        assertThat(split.get(0)).isEqualByComparingTo("4.000");
        assertThat(split.get(1)).isEqualByComparingTo("6.000");
    }

    @Test
    void givesTheRoundingRemainderToTheLastShareSoTheTotalIsExact() {
        // 10 split three ways does not divide; the parts must still add to 10.
        List<Share> shares = List.of(
                new Share(batch(1, "2026-07-01", 5, "100.000"), new BigDecimal("100.000"), 5),
                new Share(batch(2, "2026-07-02", 5, "100.000"), new BigDecimal("100.000"), 5),
                new Share(batch(3, "2026-07-03", 5, "100.000"), new BigDecimal("100.000"), 5));

        List<BigDecimal> split = FabricAllocator.splitWaste(shares, new BigDecimal("10.000"));

        assertThat(split.stream().reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("10.000");
    }

    @Test
    void splitsNothingWhenTheCutWastedNothing() {
        List<Share> shares = List.of(
                new Share(batch(1, "2026-07-12", 10, "80.000"), new BigDecimal("80.000"), 10));

        assertThat(FabricAllocator.splitWaste(shares, BigDecimal.ZERO))
                .containsExactly(BigDecimal.ZERO);
    }
}
