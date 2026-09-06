package com.apparel.tracking.fabric.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
import com.apparel.tracking.fabric.service.DerbyService.DerbyRequest;
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
 * Creating a derby: what it inherits from the fabric it belongs to, and how the
 * colours it is bought as become stock the cuts can draw on.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DerbyServiceTest {

    private static final Long TYPE_ID = 7L;

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
        cotton.setNameAr("قطن");
        cotton.setUnit(FabricUnit.KG);

        when(types.findById(TYPE_ID)).thenReturn(Optional.of(cotton));
        when(derbies.existsByFabricTypeId(TYPE_ID)).thenReturn(false);
        when(derbies.save(any(Derby.class))).thenAnswer(call -> call.getArgument(0));
        when(pricePolicy.canSeePrices()).thenReturn(true);
        when(intakeService.create(any())).thenReturn(intakeDto(101L));
    }

    /** The fabric's last regular purchase: مورد أ, at 46.000 a kilo. */
    private void fabricLastBoughtFrom(String supplierName, String price) {
        Supplier supplier = new Supplier();
        supplier.setNameAr(supplierName);

        FabricIntake latest = new FabricIntake();
        latest.setFabricType(cotton);
        latest.setSupplier(supplier);
        latest.setPricePerUnit(new BigDecimal(price));

        when(intakes.latestRegular(eq(TYPE_ID), any(Pageable.class))).thenReturn(List.of(latest));
    }

    private void fabricNeverBought() {
        when(intakes.latestRegular(eq(TYPE_ID), any(Pageable.class))).thenReturn(List.of());
    }

    private static FabricIntakeDto intakeDto(Long id) {
        return new FabricIntakeDto(
                id, TYPE_ID, "قطن", null, FabricUnit.KG, null, null, null,
                LocalDate.now(), 2, 0, 2,
                new BigDecimal("150.000"), BigDecimal.ZERO, new BigDecimal("150.000"),
                false, BigDecimal.ZERO, BigDecimal.ZERO,
                null, null, 0, 0, 0, null, List.of());
    }

    private static DerbyRequest request(DerbyColorRequest... colors) {
        return new DerbyRequest("دربي قطن", null, null, List.of(colors));
    }

    private static DerbyColorRequest colour(long colorId, String quantity) {
        return new DerbyColorRequest(colorId, new BigDecimal(quantity));
    }

    @Test
    void offersTheSupplierAndPriceTheFabricWasLastBoughtAt() {
        fabricLastBoughtFrom("مورد أ", "46.000");

        DerbyDefaultsDto defaults = service.defaultsFor(TYPE_ID);

        assertThat(defaults.supplierNameAr()).isEqualTo("مورد أ");
        assertThat(defaults.pricePerUnit()).isEqualByComparingTo("46.000");
        assertThat(defaults.unit()).isEqualTo(FabricUnit.KG);
    }

    @Test
    void offersNothingToInheritWhenTheFabricHasNeverBeenBought() {
        fabricNeverBought();

        DerbyDefaultsDto defaults = service.defaultsFor(TYPE_ID);

        assertThat(defaults.supplierId()).isNull();
        assertThat(defaults.pricePerUnit()).isNull();
    }

    @Test
    void hidesTheInheritedPriceFromAnyoneWhoMayNotSeeMoney() {
        fabricLastBoughtFrom("مورد أ", "46.000");
        when(pricePolicy.canSeePrices()).thenReturn(false);

        DerbyDefaultsDto defaults = service.defaultsFor(TYPE_ID);

        assertThat(defaults.pricePerUnit()).isNull();
        // The supplier is not money, so it still comes through.
        assertThat(defaults.supplierNameAr()).isEqualTo("مورد أ");
    }

    @Test
    void opensTheDerbyWithTheWeightOfEveryColourItWasBoughtAs() {
        fabricLastBoughtFrom("مورد أ", "46.000");

        service.create(TYPE_ID, request(colour(1L, "90.000"), colour(2L, "60.000")));

        ArgumentCaptor<FabricIntakeRequest> batch = ArgumentCaptor.forClass(FabricIntakeRequest.class);
        verify(intakeService).create(batch.capture());

        assertThat(batch.getValue().derbyPool()).isTrue();
        assertThat(batch.getValue().totalQuantity()).isEqualByComparingTo("150.000");
        // One roll per colour, so each colour can be picked up and cut separately.
        assertThat(batch.getValue().totalRolls()).isEqualTo(2);
    }

    @Test
    void recordsEachColourAsOneRollWeighingWhatWasEnteredForIt() {
        fabricLastBoughtFrom("مورد أ", "46.000");

        service.create(TYPE_ID, request(colour(1L, "90.000"), colour(2L, "60.000")));

        ArgumentCaptor<FabricIntakeColorRequest> rows =
                ArgumentCaptor.forClass(FabricIntakeColorRequest.class);
        verify(intakeService, times(2)).setColorBreakdown(eq(101L), rows.capture());

        assertThat(rows.getAllValues())
                .extracting(FabricIntakeColorRequest::fabricColorId,
                        FabricIntakeColorRequest::rollCount,
                        FabricIntakeColorRequest::quantity)
                .containsExactly(
                        tuple(1L, 1, new BigDecimal("90.000")),
                        tuple(2L, 1, new BigDecimal("60.000")));
    }

    @Test
    void inheritsSupplierAndPriceWhenTheFormLeavesThemBlank() {
        Supplier supplier = new Supplier();
        supplier.setNameAr("مورد أ");
        FabricIntake latest = new FabricIntake();
        latest.setSupplier(supplier);
        latest.setPricePerUnit(new BigDecimal("46.000"));
        latest.setFabricType(cotton);
        when(intakes.latestRegular(eq(TYPE_ID), any(Pageable.class))).thenReturn(List.of(latest));
        when(derbies.save(any(Derby.class))).thenAnswer(call -> {
            Derby saved = call.getArgument(0);
            saved.setFabricType(cotton);
            return saved;
        });

        service.create(TYPE_ID, request(colour(1L, "90.000")));

        ArgumentCaptor<FabricIntakeRequest> batch = ArgumentCaptor.forClass(FabricIntakeRequest.class);
        verify(intakeService).create(batch.capture());
        assertThat(batch.getValue().pricePerUnit()).isEqualByComparingTo("46.000");
    }

    @Test
    void keepsWhatTheFormTypedOverWhatItWouldHaveInherited() {
        fabricLastBoughtFrom("مورد أ", "46.000");

        service.create(TYPE_ID, new DerbyRequest(
                null, 99L, new BigDecimal("52.000"), List.of(colour(1L, "90.000"))));

        ArgumentCaptor<FabricIntakeRequest> batch = ArgumentCaptor.forClass(FabricIntakeRequest.class);
        verify(intakeService).create(batch.capture());

        assertThat(batch.getValue().supplierId()).isEqualTo(99L);
        assertThat(batch.getValue().pricePerUnit()).isEqualByComparingTo("52.000");
    }

    @Test
    void createsTheDerbyWithoutAPriceWhenTheFabricHasNoneToLend() {
        fabricNeverBought();

        service.create(TYPE_ID, request(colour(1L, "90.000")));

        ArgumentCaptor<FabricIntakeRequest> batch = ArgumentCaptor.forClass(FabricIntakeRequest.class);
        verify(intakeService).create(batch.capture());

        assertThat(batch.getValue().pricePerUnit()).isNull();
        assertThat(batch.getValue().supplierId()).isNull();
    }

    @Test
    void refusesASecondDerbyAndRecordsNoStockForIt() {
        when(derbies.existsByFabricTypeId(TYPE_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.create(TYPE_ID, request(colour(1L, "90.000"))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already has a derby");

        verify(intakeService, never()).create(any());
        verify(derbies, never()).save(any());
    }

    @Test
    void refusesADerbyForAFabricTypeThatDoesNotExist() {
        when(types.findById(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(TYPE_ID, request(colour(1L, "90.000"))))
                .hasMessageContaining("Fabric type");

        verify(intakeService, never()).create(any());
    }
}
