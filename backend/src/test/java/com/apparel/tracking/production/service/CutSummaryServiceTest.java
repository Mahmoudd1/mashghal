package com.apparel.tracking.production.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import com.apparel.tracking.fabric.repository.FabricColorRepository;
import com.apparel.tracking.fabric.repository.FabricIntakeRepository;
import com.apparel.tracking.production.domain.Cut;
import com.apparel.tracking.production.domain.CutColorLine;
import com.apparel.tracking.production.domain.CutEntryMode;
import com.apparel.tracking.production.domain.CutFabricDraw;
import com.apparel.tracking.production.domain.CutType;
import com.apparel.tracking.production.dto.CutColorLineRequest;
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
 * take the same fabric from the batches twice — unless it names a colour, and is
 * then drawn down that colour's batches like any other run.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CutSummaryServiceTest {

    private static final Long TYPE_ID = 3L;
    private static final Long PARENT_ID = 10L;
    private static final Long CHILD_ID = 11L;
    private static final Long BATCH_ID = 77L;
    private static final Long NAVY_ID = 5L;
    private static final Long BLACK_ID = 6L;

    @Mock private CutFabricDrawRepository draws;
    @Mock private FabricIntakeRepository intakes;
    @Mock private FabricColorRepository colors;
    @Mock private CutRepository cuts;

    private CutSummaryService service;
    private FabricType cotton;

    @BeforeEach
    void setUp() {
        service = new CutSummaryService(draws, intakes, colors, cuts);

        cotton = new FabricType();
        cotton.setId(TYPE_ID);
        cotton.setNameAr("قطن");

        when(colors.findById(NAVY_ID)).thenReturn(java.util.Optional.of(navy()));
        when(colors.findById(BLACK_ID)).thenReturn(java.util.Optional.of(black()));

        when(cuts.secondaryWeightCharged(any(), any())).thenReturn(BigDecimal.ZERO);
        when(intakes.openBatchesOldestFirst(eq(TYPE_ID), any(Boolean.class)))
                .thenReturn(List.of(batch("500.000", 50)));
        when(draws.save(any(CutFabricDraw.class))).thenAnswer(call -> call.getArgument(0));
        when(draws.weightTakenOfColor(any(), any(), any())).thenReturn(BigDecimal.ZERO);
    }

    /**
     * A derby batch holding some navy. {@code navyQuantity} null puts navy on the
     * batch without saying how much of it there is — which the breakdown allows.
     */
    private FabricIntake derbyBatch(Long id, String quantity, String navyQuantity) {
        FabricIntake batch = batch(quantity, 50);
        batch.setId(id);
        batch.setDerby(new Derby());
        batch.getColorBreakdown().add(colorRow(batch, navy(), navyQuantity));
        return batch;
    }

    private FabricColor navy() {
        FabricColor color = new FabricColor();
        color.setId(NAVY_ID);
        color.setNameAr("كحلي");
        color.setFabricType(cotton);
        return color;
    }

    private FabricColor black() {
        FabricColor color = new FabricColor();
        color.setId(BLACK_ID);
        color.setNameAr("أسود");
        color.setFabricType(cotton);
        return color;
    }

    /** A colour line of {@code weight}, with no rolls counted. */
    private CutColorLine line(Cut run, FabricColor color, String weight) {
        CutColorLine line = new CutColorLine();
        line.setCut(run);
        line.setColor(color);
        line.setWeight(new BigDecimal(weight));
        return line;
    }

    /** A derby run of {@code weight}, all of it navy. */
    private Cut derbyRun(String weight) {
        Cut run = cut(CHILD_ID, CutType.DERBY, weight, mainCut("200.000"));
        run.setTotalRolls(null);
        run.setReusedRolls(null);
        run.getColorLines().add(line(run, navy(), weight));
        return run;
    }

    private CutColorLineRequest navyLine(String weight) {
        return new CutColorLineRequest(NAVY_ID, new BigDecimal(weight), null, null);
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
    void walksOneColoursBatchesOldestFirst() {
        // 5 kg of navy on the older batch, 8 on the next: a 12 kg run takes all
        // of the first and spills the rest into the second.
        FabricIntake older = derbyBatch(BATCH_ID, "40.000", "5.000");
        FabricIntake newer = derbyBatch(78L, "40.000", "8.000");
        when(intakes.openBatchesOldestFirst(eq(TYPE_ID), eq(true)))
                .thenReturn(List.of(older, newer));

        service.apply(derbyRun("12.000"));

        verify(draws, times(2)).save(any(CutFabricDraw.class));
        assertThat(older.getConsumedQuantity()).isEqualByComparingTo("5.000");
        assertThat(newer.getConsumedQuantity()).isEqualByComparingTo("7.000");
    }

    @Test
    void walksPastABatchThatNeverListedTheColour() {
        // A batch that does not say it holds navy cannot be said to hold any.
        FabricIntake plain = batch("40.000", 50);
        plain.setId(BATCH_ID);
        plain.setDerby(new Derby());
        FabricIntake navyBatch = derbyBatch(78L, "40.000", "9.000");
        when(intakes.openBatchesOldestFirst(eq(TYPE_ID), eq(true)))
                .thenReturn(List.of(plain, navyBatch));

        service.apply(derbyRun("7.500"));

        assertThat(plain.getConsumedQuantity()).isEqualByComparingTo("0");
        assertThat(navyBatch.getConsumedQuantity()).isEqualByComparingTo("7.500");
    }

    @Test
    void countsWhatEarlierRunsAlreadyTookOfThatColour() {
        FabricIntake batch = derbyBatch(BATCH_ID, "40.000", "9.000");
        when(intakes.openBatchesOldestFirst(eq(TYPE_ID), eq(true))).thenReturn(List.of(batch));
        when(draws.weightTakenOfColor(eq(BATCH_ID), eq(NAVY_ID), any()))
                .thenReturn(new BigDecimal("6.000"));

        // 9 kg navy, 6 already cut: only 3 left, and no other batch holds any.
        assertThatThrownBy(() -> service.apply(derbyRun("7.500")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("كحلي");
    }

    @Test
    void takesTheWholeBatchWhenTheBreakdownNeverWeighedTheColour() {
        // The breakdown is soft: a colour given as a roll count says nothing about
        // the kilos, so the batch's own remainder is the only cap there is.
        FabricIntake batch = derbyBatch(BATCH_ID, "40.000", null);
        when(intakes.openBatchesOldestFirst(eq(TYPE_ID), eq(true))).thenReturn(List.of(batch));

        service.apply(derbyRun("30.000"));

        assertThat(batch.getConsumedQuantity()).isEqualByComparingTo("30.000");
    }

    @Test
    void drawsASecondaryRunFromTheShelfWhenItNamesAColour() {
        FabricIntake batch = derbyBatch(BATCH_ID, "500.000", "80.000");
        batch.setDerby(null);
        when(intakes.openBatchesOldestFirst(eq(TYPE_ID), eq(false))).thenReturn(List.of(batch));
        Cut run = cut(CHILD_ID, CutType.SECONDARY, "50.000", mainCut("200.000"));
        run.getColorLines().add(line(run, navy(), "50.000"));

        service.apply(run);

        // It took its own fabric off the shelf, so there is nothing to charge
        // against the main cut it hangs off.
        verify(cuts, never()).secondaryWeightCharged(any(), any());
        verify(draws).save(any(CutFabricDraw.class));
    }

    @Test
    void refusesADerbyRunThatNamesNoColour() {
        Cut run = cut(CHILD_ID, CutType.DERBY, "7.500", mainCut("200.000"));

        assertThatThrownBy(() -> service.assignColorLines(run, List.of()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("which colour");
    }

    @Test
    void refusesAColourOfAnotherFabric() {
        FabricType linen = new FabricType();
        linen.setId(9L);
        linen.setNameAr("كتان");
        Cut run = cut(CHILD_ID, CutType.DERBY, "7.500", mainCut("200.000"));
        run.setFabricType(linen);

        assertThatThrownBy(() -> service.assignColorLines(run, List.of(navyLine("7.500"))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not of the fabric");
    }

    @Test
    void takesTheFabricTypeFromTheColourWhenTheRunNeverNamedOne() {
        Cut run = cut(CHILD_ID, CutType.DERBY, "7.500", mainCut("200.000"));
        run.setFabricType(null);

        service.assignColorLines(run, List.of(navyLine("7.500")));

        assertThat(run.getFabricType()).isEqualTo(cotton);
        assertThat(run.getColorLines()).singleElement()
                .satisfies(line -> assertThat(line.getColor().getId()).isEqualTo(NAVY_ID));
    }

    @Test
    void walksEachColourDownItsOwnBatches() {
        // One purchase holding both: 5 kg navy and 6 kg black. The older batch
        // has only navy; the newer one both.
        FabricIntake older = derbyBatch(BATCH_ID, "40.000", "4.000");
        FabricIntake newer = derbyBatch(78L, "40.000", "5.000");
        newer.getColorBreakdown().add(colorRow(newer, black(), "6.000"));
        when(intakes.openBatchesOldestFirst(eq(TYPE_ID), eq(true)))
                .thenReturn(List.of(older, newer));

        Cut run = derbyRun("9.000");
        run.getColorLines().clear();
        run.getColorLines().add(line(run, navy(), "6.000"));
        run.getColorLines().add(line(run, black(), "3.000"));

        service.apply(run);

        // Navy: 4 off the older batch, 2 spilling into the newer. Black only
        // exists on the newer, so all 3 come from there.
        assertThat(older.getConsumedQuantity()).isEqualByComparingTo("4.000");
        assertThat(newer.getConsumedQuantity()).isEqualByComparingTo("5.000");
        verify(draws, times(3)).save(any(CutFabricDraw.class));
    }

    @Test
    void letsASecondColourSeeWhatTheFirstTookOffASharedBatch() {
        // 10 kg left on the batch in all, the breakdown silent on the weights: if
        // navy takes 7, black can only have the 3 that remain.
        FabricIntake shared = derbyBatch(BATCH_ID, "10.000", null);
        shared.getColorBreakdown().add(colorRow(shared, black(), null));
        when(intakes.openBatchesOldestFirst(eq(TYPE_ID), eq(true))).thenReturn(List.of(shared));

        Cut run = derbyRun("11.000");
        run.getColorLines().clear();
        run.getColorLines().add(line(run, navy(), "7.000"));
        run.getColorLines().add(line(run, black(), "4.000"));

        assertThatThrownBy(() -> service.apply(run))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("أسود");
    }

    @Test
    void refusesTheSameColourOnTwoLines() {
        Cut run = cut(CHILD_ID, CutType.DERBY, "7.500", mainCut("200.000"));

        assertThatThrownBy(() -> service.assignColorLines(
                        run, List.of(navyLine("3.000"), navyLine("4.500"))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("one line");
    }

    @Test
    void keepsALineInPlaceWhenAnEditChangesItsWeight() {
        Cut run = derbyRun("7.500");
        CutColorLine original = run.getColorLines().get(0);

        service.assignColorLines(run, List.of(navyLine("9.000")));

        // Changed where it stands, not deleted and re-added: the one-line-per-
        // colour key would refuse the insert, which is flushed before the delete.
        assertThat(run.getColorLines()).containsExactly(original);
        assertThat(original.getWeight()).isEqualByComparingTo("9.000");
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
