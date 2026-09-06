package com.apparel.tracking.fabric.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.apparel.tracking.common.exception.BusinessRuleException;
import com.apparel.tracking.common.security.PricePolicy;
import com.apparel.tracking.fabric.domain.Derby;
import com.apparel.tracking.fabric.domain.FabricIntake;
import com.apparel.tracking.fabric.domain.FabricType;
import com.apparel.tracking.fabric.domain.FabricUnit;
import com.apparel.tracking.fabric.dto.DerbyDefaultsDto;
import com.apparel.tracking.fabric.dto.FabricIntakeColorRequest;
import com.apparel.tracking.fabric.dto.FabricIntakeDto;
import com.apparel.tracking.fabric.dto.FabricIntakeRequest;
import com.apparel.tracking.fabric.repository.DerbyRepository;
import com.apparel.tracking.fabric.repository.FabricIntakeRepository;
import com.apparel.tracking.fabric.repository.FabricTypeRepository;
import com.apparel.tracking.fabric.service.DerbyService.DerbyColorRequest;
import com.apparel.tracking.fabric.service.DerbyService.DerbyOnPurchaseRequest;
import com.apparel.tracking.fabric.service.DerbyService.DerbyPurchaseRequest;
import com.apparel.tracking.supplier.domain.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import org.springframework.data.domain.Pageable;

/**
 * Buying derby: with the fabric it belongs to, or on its own.
 *
 * <p>The first is the normal case and the one with the most to get wrong — the
 * derby has to take the parent purchase's date, supplier and price rather than
 * asking for them again.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DerbyServiceTest {

    private static final Long TYPE_ID = 7L;
    private static final Long PARENT_ID = 33L;
    private static final Long DERBY_BATCH_ID = 101L;
    private static final LocalDate BOUGHT_ON = LocalDate.of(2026, 8, 26);

    @Mock private DerbyRepository derbies;
    @Mock private FabricTypeRepository types;
    @Mock private FabricIntakeRepository intakes;
    @Mock private FabricIntakeService intakeService;
    @Mock private PricePolicy pricePolicy;

    private DerbyService service;
    private FabricType cotton;

    @BeforeEach
    void setUp() {
        service = new DerbyService(derbies, types, intakes, intakeService, pricePolicy);

        cotton = new FabricType();
        // The id matters: the pool is looked up by it, and the derby batch is
        // recorded against it. A type without one silently opens a second pool.
        cotton.setId(TYPE_ID);
        cotton.setNameAr("قطن");
        cotton.setUnit(FabricUnit.KG);

        when(types.findById(TYPE_ID)).thenReturn(Optional.of(cotton));
        when(derbies.findByFabricTypeId(TYPE_ID)).thenReturn(Optional.of(existingPool()));
        when(derbies.save(any(Derby.class))).thenAnswer(call -> call.getArgument(0));
        when(pricePolicy.canSeePrices()).thenReturn(true);
        when(intakeService.create(any())).thenReturn(batchDto());
        when(intakeService.get(DERBY_BATCH_ID)).thenReturn(batchDto());
        when(intakes.existsByParentIntakeId(PARENT_ID)).thenReturn(false);
    }

    private Derby existingPool() {
        Derby pool = new Derby();
        pool.setFabricType(cotton);
        return pool;
    }

    /** A fabric purchase: 26/08, from مورد أ, at 46.000 a kilo. */
    private FabricIntake parentPurchase() {
        Supplier supplier = new Supplier();
        supplier.setNameAr("مورد أ");

        FabricIntake parent = new FabricIntake();
        parent.setFabricType(cotton);
        parent.setSupplier(supplier);
        parent.setIntakeDate(BOUGHT_ON);
        parent.setPricePerUnit(new BigDecimal("46.000"));
        when(intakes.findById(PARENT_ID)).thenReturn(Optional.of(parent));
        when(intakes.findById(DERBY_BATCH_ID)).thenReturn(Optional.of(new FabricIntake()));
        return parent;
    }

    private static FabricIntakeDto batchDto() {
        return new FabricIntakeDto(
                DERBY_BATCH_ID, TYPE_ID, "قطن", null, FabricUnit.KG, null, null, null,
                BOUGHT_ON, null, 2, 0, 2,
                new BigDecimal("150.000"), BigDecimal.ZERO, new BigDecimal("150.000"),
                false, BigDecimal.ZERO, BigDecimal.ZERO,
                null, null, 0, 0, 0, null, List.of());
    }

    private static DerbyColorRequest colour(long colorId, String quantity) {
        return new DerbyColorRequest(colorId, new BigDecimal(quantity));
    }

    private static DerbyOnPurchaseRequest withPurchase(DerbyColorRequest... colors) {
        return new DerbyOnPurchaseRequest("دربي قطن", null, List.of(colors));
    }

    private FabricIntakeRequest capturedBatch() {
        ArgumentCaptor<FabricIntakeRequest> batch = ArgumentCaptor.forClass(FabricIntakeRequest.class);
        verify(intakeService).create(batch.capture());
        return batch.getValue();
    }

    // --- bought with the fabric ---------------------------------------------

    @Test
    void takesTheDateSupplierAndPriceOfThePurchaseItCameWith() {
        parentPurchase();

        service.addToPurchase(PARENT_ID, withPurchase(colour(1L, "90.000")));

        FabricIntakeRequest batch = capturedBatch();
        assertThat(batch.intakeDate()).isEqualTo(BOUGHT_ON);
        assertThat(batch.pricePerUnit()).isEqualByComparingTo("46.000");
        assertThat(batch.derbyPool()).isTrue();
    }

    @Test
    void takesAPriceOfItsOwnWhenTheDerbyActuallyCostSomethingElse() {
        parentPurchase();

        service.addToPurchase(PARENT_ID,
                new DerbyOnPurchaseRequest(null, new BigDecimal("52.000"), List.of(colour(1L, "90.000"))));

        assertThat(capturedBatch().pricePerUnit()).isEqualByComparingTo("52.000");
    }

    @Test
    void weighsTheBatchAtTheSumOfItsColours() {
        parentPurchase();

        service.addToPurchase(PARENT_ID, withPurchase(colour(1L, "90.000"), colour(2L, "60.000")));

        FabricIntakeRequest batch = capturedBatch();
        assertThat(batch.totalQuantity()).isEqualByComparingTo("150.000");
        // One roll per colour, so each colour can be picked up and cut separately.
        assertThat(batch.totalRolls()).isEqualTo(2);
    }

    @Test
    void recordsEachColourAsOneRollWeighingWhatWasEnteredForIt() {
        parentPurchase();

        service.addToPurchase(PARENT_ID, withPurchase(colour(1L, "90.000"), colour(2L, "60.000")));

        ArgumentCaptor<FabricIntakeColorRequest> rows =
                ArgumentCaptor.forClass(FabricIntakeColorRequest.class);
        verify(intakeService, times(2)).setColorBreakdown(eq(DERBY_BATCH_ID), rows.capture());

        assertThat(rows.getAllValues())
                .extracting(FabricIntakeColorRequest::fabricColorId,
                        FabricIntakeColorRequest::rollCount,
                        FabricIntakeColorRequest::quantity)
                .containsExactly(
                        tuple(1L, 1, new BigDecimal("90.000")),
                        tuple(2L, 1, new BigDecimal("60.000")));
    }

    @Test
    void refusesASecondDerbyOnTheSamePurchase() {
        parentPurchase();
        when(intakes.existsByParentIntakeId(PARENT_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.addToPurchase(PARENT_ID, withPurchase(colour(1L, "90.000"))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already has a derby");

        verify(intakeService, never()).create(any());
    }

    @Test
    void refusesToHangADerbyOffAnotherDerby() {
        FabricIntake derbyBatch = new FabricIntake();
        derbyBatch.setFabricType(cotton);
        derbyBatch.setDerby(existingPool());
        when(intakes.findById(PARENT_ID)).thenReturn(Optional.of(derbyBatch));

        assertThatThrownBy(() -> service.addToPurchase(PARENT_ID, withPurchase(colour(1L, "90.000"))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("itself derby stock");

        verify(intakeService, never()).create(any());
    }

    // --- bought on its own ---------------------------------------------------

    @Test
    void recordsAStandaloneDerbyAgainstTheFabricTypeAndNoPurchase() {
        when(intakes.latestRegular(eq(TYPE_ID), any(Pageable.class))).thenReturn(List.of());

        service.recordPurchase(TYPE_ID, new DerbyPurchaseRequest(
                BOUGHT_ON, null, 99L, new BigDecimal("52.000"), List.of(colour(1L, "90.000"))));

        FabricIntakeRequest batch = capturedBatch();
        assertThat(batch.fabricTypeId()).isEqualTo(TYPE_ID);
        assertThat(batch.supplierId()).isEqualTo(99L);
        assertThat(batch.pricePerUnit()).isEqualByComparingTo("52.000");
    }

    @Test
    void fallsBackToTheFabricsLastPurchaseWhenAStandaloneDerbyNamesNoSupplierOrPrice() {
        Supplier supplier = new Supplier();
        supplier.setNameAr("مورد أ");
        FabricIntake latest = new FabricIntake();
        latest.setFabricType(cotton);
        latest.setSupplier(supplier);
        latest.setPricePerUnit(new BigDecimal("46.000"));
        when(intakes.latestRegular(eq(TYPE_ID), any(Pageable.class))).thenReturn(List.of(latest));

        service.recordPurchase(TYPE_ID, new DerbyPurchaseRequest(
                BOUGHT_ON, null, null, null, List.of(colour(1L, "90.000"))));

        assertThat(capturedBatch().pricePerUnit()).isEqualByComparingTo("46.000");
    }

    // --- the pool ------------------------------------------------------------

    @Test
    void opensThePoolOnTheFirstDerbyWithoutBeingAsked() {
        parentPurchase();
        when(derbies.findByFabricTypeId(TYPE_ID)).thenReturn(Optional.empty());

        service.addToPurchase(PARENT_ID, withPurchase(colour(1L, "90.000")));

        verify(derbies).save(any(Derby.class));
        verify(intakeService).create(any());
    }

    @Test
    void reusesThePoolOnEveryDerbyAfterTheFirst() {
        parentPurchase();

        service.addToPurchase(PARENT_ID, withPurchase(colour(1L, "90.000")));

        verify(derbies, never()).save(any(Derby.class));
    }

    // --- defaults for the standalone form ------------------------------------

    @Test
    void offersTheSupplierAndPriceTheFabricWasLastBoughtAt() {
        Supplier supplier = new Supplier();
        supplier.setNameAr("مورد أ");
        FabricIntake latest = new FabricIntake();
        latest.setFabricType(cotton);
        latest.setSupplier(supplier);
        latest.setPricePerUnit(new BigDecimal("46.000"));
        when(intakes.latestRegular(eq(TYPE_ID), any(Pageable.class))).thenReturn(List.of(latest));

        DerbyDefaultsDto defaults = service.defaultsFor(TYPE_ID);

        assertThat(defaults.supplierNameAr()).isEqualTo("مورد أ");
        assertThat(defaults.pricePerUnit()).isEqualByComparingTo("46.000");
    }

    @Test
    void hidesTheInheritedPriceFromAnyoneWhoMayNotSeeMoney() {
        FabricIntake latest = new FabricIntake();
        latest.setFabricType(cotton);
        latest.setPricePerUnit(new BigDecimal("46.000"));
        when(intakes.latestRegular(eq(TYPE_ID), any(Pageable.class))).thenReturn(List.of(latest));
        when(pricePolicy.canSeePrices()).thenReturn(false);

        assertThat(service.defaultsFor(TYPE_ID).pricePerUnit()).isNull();
    }
}
