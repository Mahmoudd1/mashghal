package com.apparel.tracking.production.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.apparel.tracking.common.exception.BusinessRuleException;
import com.apparel.tracking.fabric.domain.Derby;
import com.apparel.tracking.fabric.domain.FabricColor;
import com.apparel.tracking.fabric.domain.FabricIntake;
import com.apparel.tracking.fabric.domain.FabricIntakeColor;
import com.apparel.tracking.fabric.domain.FabricType;
import com.apparel.tracking.fabric.repository.FabricIntakeRepository;
import com.apparel.tracking.production.domain.Cut;
import com.apparel.tracking.production.domain.CutEntryMode;
import com.apparel.tracking.production.domain.CutFabricDraw;
import com.apparel.tracking.production.domain.CutType;
import com.apparel.tracking.production.repository.CutFabricDrawRepository;
import com.apparel.tracking.production.repository.CutRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Where a cut's fabric comes from when it was recorded from its totals.
 *
 * <p>A main run takes it off the shelf. A secondary run does not: it is cut from
 * the remnants of the rolls the main run already drew, so drawing again would
 * take the same fabric from the batches twice.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CutSummaryServiceTest {

    private static final Long TYPE_ID = 3L;
    private static final Long PARENT_ID = 10L;
    private static final Long CHILD_ID = 11L;
    private static final Long BATCH_ID = 77L;
    private static final Long NAVY_ID = 5L;

    @Mock private CutFabricDrawRepository draws;
    @Mock private FabricIntakeRepository intakes;
    @Mock private CutRepository cuts;

    private CutSummaryService service;
    private FabricType cotton;

    @BeforeEach
    void setUp() {
        service = new CutSummaryService(draws, intakes, cuts);

        cotton = new FabricType();
        cotton.setId(TYPE_ID);
        cotton.setNameAr("قطن");

        when(cuts.secondaryWeightCharged(any(), any())).thenReturn(BigDecimal.ZERO);
        when(intakes.openBatchesOldestFirst(eq(TYPE_ID), any(Boolean.class)))
                .thenReturn(List.of(batch("500.000", 50)));
        when(draws.save(any(CutFabricDraw.class))).thenAnswer(call -> call.getArgument(0));
        when(draws.weightTakenOfColor(any(), any(), any())).thenReturn(BigDecimal.ZERO);
    }

    /** A derby batch of one colour, with as much of it as the tests need. */
    private FabricIntake derbyBatch(String quantity, String navyQuantity) {
        FabricIntake batch = batch(quantity, 50);
        batch.setId(BATCH_ID);
        batch.setDerby(new Derby());
        if (navyQuantity != null) {
            batch.getColorBreakdown().add(colorRow(batch, navy(), navyQuantity));
        }
        return batch;
    }

    private FabricColor navy() {
        FabricColor color = new FabricColor();
        color.setId(NAVY_ID);
        color.setNameAr("كحلي");
        return color;
    }

    private FabricIntakeColor colorRow(FabricIntake batch, FabricColor color, String quantity) {
        FabricIntakeColor row = new FabricIntakeColor();
        row.setIntake(batch);
        row.setColor(color);
        row.setRollCount(10);
        row.setQuantity(quantity == null ? null : new BigDecimal(quantity));
        return row;
    }

    private FabricIntake batch(String quantity, int rolls) {
        FabricIntake intake = new FabricIntake();
        intake.setId(99L);
        intake.setFabricType(cotton);
        intake.setIntakeDate(LocalDate.of(2026, 7, 12));
        intake.setTotalRolls(rolls);
        intake.setTotalQuantity(new BigDecimal(quantity));
        intake.setConsumedQuantity(BigDecimal.ZERO);
        intake.setWastedQuantity(BigDecimal.ZERO);
        return intake;
    }

    private Cut cut(Long id, CutType type, String weight, Cut parent) {
        Cut cut = new Cut();
        cut.setId(id);
        cut.setCutNumber(type == CutType.MAIN ? "CUT-1" : "CUT-1S");
        cut.setCutType(type);
        cut.setFabricType(cotton);
        cut.setEntryMode(CutEntryMode.SUMMARY);
        cut.setTotalRolls(10);
        cut.setReusedRolls(0);
        cut.setTotalWeight(new BigDecimal(weight));
        cut.setWasteWeight(BigDecimal.ZERO);
        cut.setTotalLayers(100);
        if (parent != null) {
            cut.assignParent(parent);
        }
        return cut;
    }

    private Cut mainCut(String weight) {
        return cut(PARENT_ID, CutType.MAIN, weight, null);
    }

    @Test
    void takesAMainRunsFabricOffTheShelf() {
        service.apply(mainCut("200.000"));

        verify(draws).save(any(CutFabricDraw.class));
    }

    @Test
    void takesNothingOffTheShelfForASecondaryRun() {
        Cut parent = mainCut("200.000");

        service.apply(cut(CHILD_ID, CutType.SECONDARY, "50.000", parent));

        // The fabric was drawn once already, with the main run.
        verify(draws, never()).save(any(CutFabricDraw.class));
    }

    @Test
    void chargesASecondaryRunAgainstTheMainCutItIsSpentFrom() {
        Cut parent = mainCut("200.000");
        when(cuts.secondaryWeightCharged(eq(PARENT_ID), eq(CHILD_ID)))
                .thenReturn(new BigDecimal("30.000"));

        // 30 already charged by a sibling, 50 more: 80 of the main cut's 200.
        service.apply(cut(CHILD_ID, CutType.SECONDARY, "50.000", parent));

        verify(cuts).secondaryWeightCharged(PARENT_ID, CHILD_ID);
    }

    @Test
    void refusesASecondaryRunBiggerThanTheMainCutItComesFrom() {
        Cut parent = mainCut("200.000");

        assertThatThrownBy(() -> service.apply(cut(CHILD_ID, CutType.SECONDARY, "250.000", parent)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("would spend");
    }

    @Test
    void refusesASecondaryRunThatSiblingsHaveAlreadyUsedUpTheRoomFor() {
        Cut parent = mainCut("200.000");
        when(cuts.secondaryWeightCharged(eq(PARENT_ID), eq(CHILD_ID)))
                .thenReturn(new BigDecimal("180.000"));

        assertThatThrownBy(() -> service.apply(cut(CHILD_ID, CutType.SECONDARY, "50.000", parent)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("would spend");
    }

    @Test
    void leavesTheRowBeingEditedOutOfItsOwnHeadroomCheck() {
        Cut parent = mainCut("200.000");
        Cut child = cut(CHILD_ID, CutType.SECONDARY, "150.000", parent);

        // Its own old 150 must not count against it, or no edit would ever fit.
        service.apply(child);

        verify(cuts).secondaryWeightCharged(PARENT_ID, CHILD_ID);
    }

    @Test
    void measuresADetailedMainCutByWhatItsRollsActuallyTook() {
        Cut parent = mainCut("0");
        parent.setEntryMode(CutEntryMode.DETAILED);
        parent.setTotalWeight(null);
        when(cuts.rollWeightOffTheShelf(PARENT_ID)).thenReturn(new BigDecimal("300.000"));

        service.apply(cut(CHILD_ID, CutType.SECONDARY, "120.000", parent));

        verify(cuts).rollWeightOffTheShelf(PARENT_ID);
        verify(draws, never()).save(any(CutFabricDraw.class));
    }

    @Test
    void takesADerbyRunOffTheBatchItNames() {
        // Derby is kept and asked for by colour, so the run says which purchase
        // it left rather than having the oldest one guessed for it.
        FabricIntake batch = derbyBatch("40.000", "25.000");
        Cut run = cut(CHILD_ID, CutType.DERBY, "7.500", mainCut("200.000"));
        run.setFabricIntake(batch);
        run.setFabricColor(navy());

        service.apply(run);

        verify(draws).save(any(CutFabricDraw.class));
        assertThat(batch.getConsumedQuantity()).isEqualByComparingTo("7.500");
    }

    @Test
    void refusesMoreOfAColourThanTheBatchHoldsOfIt() {
        FabricIntake batch = derbyBatch("40.000", "5.000");
        when(draws.weightTakenOfColor(eq(BATCH_ID), eq(NAVY_ID), any()))
                .thenReturn(new BigDecimal("2.000"));
        Cut run = cut(CHILD_ID, CutType.DERBY, "7.500", mainCut("200.000"));
        run.setFabricIntake(batch);
        run.setFabricColor(navy());

        // 5 kg navy on the batch, 2 already cut, and this run wants 7.5 more.
        assertThatThrownBy(() -> service.apply(run))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already cut");
    }

    @Test
    void leavesAColourAloneWhenTheBreakdownNeverSaidHowMuchOfItThereIs() {
        // The breakdown is a soft record: a colour given as a roll count says
        // nothing about the kilos, so only the batch total can be checked.
        FabricIntake batch = derbyBatch("40.000", null);
        batch.getColorBreakdown().add(colorRow(batch, navy(), null));
        Cut run = cut(CHILD_ID, CutType.DERBY, "7.500", mainCut("200.000"));
        run.setFabricIntake(batch);
        run.setFabricColor(navy());

        service.apply(run);

        verify(draws).save(any(CutFabricDraw.class));
    }

    @Test
    void spendsASecondaryRunFromItsOwnBatchWhenItNamesOne() {
        FabricIntake batch = batch("500.000", 50);
        batch.setId(BATCH_ID);
        Cut run = cut(CHILD_ID, CutType.SECONDARY, "50.000", mainCut("200.000"));
        run.setFabricIntake(batch);

        service.apply(run);

        // It took its own fabric off the shelf, so there is nothing to charge
        // against the main cut it hangs off.
        verify(cuts, never()).secondaryWeightCharged(any(), any());
        verify(draws).save(any(CutFabricDraw.class));
    }

    @Test
    void refusesADerbyRunThatNamesNoBatch() {
        Cut run = cut(CHILD_ID, CutType.DERBY, "7.500", mainCut("200.000"));

        assertThatThrownBy(() -> service.assignSource(run, null, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("derby batch");
    }

    @Test
    void refusesARunCutFromTheWrongPool() {
        when(intakes.findById(BATCH_ID)).thenReturn(java.util.Optional.of(batch("500.000", 50)));
        Cut run = cut(CHILD_ID, CutType.DERBY, "7.500", mainCut("200.000"));

        assertThatThrownBy(() -> service.assignSource(run, BATCH_ID, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("derby batch");
    }

    @Test
    void asksWhichColourOfTheBatchOnceTheBatchSaysWhatItHolds() {
        when(intakes.findById(BATCH_ID)).thenReturn(java.util.Optional.of(derbyBatch("40.000", "25.000")));
        Cut run = cut(CHILD_ID, CutType.DERBY, "7.500", mainCut("200.000"));

        assertThatThrownBy(() -> service.assignSource(run, BATCH_ID, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("which colour");
    }

    @Test
    void refusesAColourTheNamedBatchDoesNotHold() {
        when(intakes.findById(BATCH_ID)).thenReturn(java.util.Optional.of(derbyBatch("40.000", "25.000")));
        Cut run = cut(CHILD_ID, CutType.DERBY, "7.500", mainCut("200.000"));

        assertThatThrownBy(() -> service.assignSource(run, BATCH_ID, 999L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("no fabric of that colour");
    }

    @Test
    void takesTheFabricTypeFromTheBatchWhenTheRunNeverNamedOne() {
        when(intakes.findById(BATCH_ID)).thenReturn(java.util.Optional.of(derbyBatch("40.000", "25.000")));
        Cut run = cut(CHILD_ID, CutType.DERBY, "7.500", mainCut("200.000"));
        run.setFabricType(null);

        service.assignSource(run, BATCH_ID, NAVY_ID);

        assertThat(run.getFabricType()).isEqualTo(cotton);
        assertThat(run.getFabricColor().getId()).isEqualTo(NAVY_ID);
    }

    @Test
    void refusesASecondaryRunThatNamesNoMainCut() {
        assertThatThrownBy(() -> service.apply(cut(CHILD_ID, CutType.SECONDARY, "50.000", null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("MAIN cut");
    }

    @Test
    void readsWhatAMainCutsChildrenHaveSpentOfIt() {
        when(cuts.secondaryWeightCharged(eq(PARENT_ID), isNull()))
                .thenReturn(new BigDecimal("80.000"));

        assertThat(service.chargedByChildren(PARENT_ID)).isEqualByComparingTo("80.000");
    }
}
