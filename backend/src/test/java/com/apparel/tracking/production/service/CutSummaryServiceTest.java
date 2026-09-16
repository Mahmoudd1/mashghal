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
import com.apparel.tracking.fabric.domain.FabricIntake;
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
    void stillDrawsDerbyStockForADerbyRun() {
        // Derby is a different material from a different pool; the main cut's
        // weight never included it.
        Cut parent = mainCut("200.000");
        service.apply(cut(CHILD_ID, CutType.DERBY, "7.500", parent));

        verify(draws).save(any(CutFabricDraw.class));
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
