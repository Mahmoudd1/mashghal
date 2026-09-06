package com.apparel.tracking.fabric.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.apparel.tracking.common.exception.BusinessRuleException;
import com.apparel.tracking.common.exception.NotFoundException;
import com.apparel.tracking.common.security.PricePolicy;
import com.apparel.tracking.fabric.domain.Derby;
import com.apparel.tracking.fabric.domain.FabricIntake;
import com.apparel.tracking.fabric.domain.FabricType;
import com.apparel.tracking.fabric.dto.DerbyDefaultsDto;
import com.apparel.tracking.fabric.dto.DerbyDto;
import com.apparel.tracking.fabric.dto.FabricIntakeColorRequest;
import com.apparel.tracking.fabric.dto.FabricIntakeDto;
import com.apparel.tracking.fabric.dto.FabricIntakeRequest;
import com.apparel.tracking.fabric.repository.DerbyRepository;
import com.apparel.tracking.fabric.repository.FabricIntakeRepository;
import com.apparel.tracking.fabric.repository.FabricTypeRepository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Buying derby.
 *
 * <p>Derby is bought the way fabric is: on a date, from a supplier, at a price —
 * most often in the same transaction as the fabric itself, occasionally on its
 * own from somebody else. Each purchase is its own dated batch, and they pool
 * together under the fabric type. The pool is a bookkeeping record, created the
 * first time one is needed and never asked for.
 *
 * <p>A derby is bought as a set of colours with a weight each, not as a roll
 * count, so that is what the form asks for. Underneath it is still an ordinary
 * dated purchase against the pool — which is what makes derby stock visible to
 * the cuts, the remaining report and the waste figures without any of them
 * needing to know a derby exists.
 */
@Service
@Transactional
public class DerbyService {

    /**
     * A derby bought together with a fabric purchase.
     *
     * <p>Date and supplier are the parent purchase's and are not restated here —
     * that is what "bought with the fabric" means. The price is the parent's too
     * unless this says otherwise.
     *
     * @param pricePerUnit null takes the price the fabric itself was bought at
     */
    public record DerbyOnPurchaseRequest(
            @Size(max = 512) String note,
            @DecimalMin("0.0") @Digits(integer = 9, fraction = 3) BigDecimal pricePerUnit,
            @NotEmpty @Valid List<DerbyColorRequest> colors) {
    }

    /**
     * A derby bought on its own, from whoever sold it.
     *
     * <p>It belongs to the fabric type and to no particular purchase of it, so it
     * carries its own date, supplier and price.
     *
     * @param supplierId   null falls back to whoever supplied the fabric last
     * @param pricePerUnit null falls back to what the fabric last cost
     */
    public record DerbyPurchaseRequest(
            @NotNull @PastOrPresent LocalDate intakeDate,
            @Size(max = 512) String note,
            Long supplierId,
            @DecimalMin("0.0") @Digits(integer = 9, fraction = 3) BigDecimal pricePerUnit,
            @NotEmpty @Valid List<DerbyColorRequest> colors) {
    }

    /** One colour of a derby, and how much of it there is. */
    public record DerbyColorRequest(
            @NotNull Long fabricColorId,
            @NotNull @DecimalMin("0.001") @Digits(integer = 11, fraction = 3) BigDecimal quantity) {
    }

    /** Editing a derby after the fact only ever touches its note. */
    public record DerbyNoteRequest(@Size(max = 512) String note) {
    }

    private final DerbyRepository derbies;
    private final FabricTypeRepository types;
    private final FabricIntakeRepository intakes;
    private final FabricIntakeService intakeService;
    private final PricePolicy pricePolicy;

    public DerbyService(
            DerbyRepository derbies,
            FabricTypeRepository types,
            FabricIntakeRepository intakes,
            FabricIntakeService intakeService,
            PricePolicy pricePolicy) {
        this.derbies = derbies;
        this.types = types;
        this.intakes = intakes;
        this.intakeService = intakeService;
        this.pricePolicy = pricePolicy;
    }

    @Transactional(readOnly = true)
    public List<DerbyDto> list() {
        return derbies.findAll().stream().map(DerbyDto::from).toList();
    }

    @Transactional(readOnly = true)
    public DerbyDto getForFabricType(Long fabricTypeId) {
        return derbies.findByFabricTypeId(fabricTypeId)
                .map(DerbyDto::from)
                .orElseThrow(() -> new NotFoundException("This fabric type has no derby"));
    }

    /**
     * What the form should show before anything is typed.
     *
     * <p>Both figures come back null when the fabric has never been bought — there
     * is nothing to inherit yet — and the price is blanked for anyone who is not
     * allowed to see money, exactly as it is everywhere else.
     */
    @Transactional(readOnly = true)
    public DerbyDefaultsDto defaultsFor(Long fabricTypeId) {
        FabricType type = requireType(fabricTypeId);
        FabricIntake latest = latestRegularIntake(fabricTypeId);

        DerbyDefaultsDto defaults = new DerbyDefaultsDto(
                latest == null || latest.getSupplier() == null ? null : latest.getSupplier().getId(),
                latest == null || latest.getSupplier() == null ? null : latest.getSupplier().getNameAr(),
                latest == null ? null : latest.getPricePerUnit(),
                type.getUnit());

        return pricePolicy.canSeePrices() ? defaults : defaults.withoutPrices();
    }

    /**
     * Records a derby bought together with an existing fabric purchase.
     *
     * <p>This is the usual case: the derby came on the same day, from the same
     * supplier, at the same price, so none of that is asked for again — it is
     * read off the purchase being added to.
     */
    public FabricIntakeDto addToPurchase(Long parentIntakeId, DerbyOnPurchaseRequest request) {
        FabricIntake parent = intakes.findById(parentIntakeId)
                .orElseThrow(() -> NotFoundException.of("Fabric intake", parentIntakeId));

        if (parent.isDerbyPool()) {
            throw new BusinessRuleException("derby_parent_is_derby",
                    "That purchase is itself derby stock; a derby cannot have a derby");
        }
        if (intakes.existsByParentIntakeId(parentIntakeId)) {
            throw new BusinessRuleException("derby_already_on_purchase",
                    "The %s purchase already has a derby recorded against it"
                            .formatted(parent.getIntakeDate()));
        }

        return record(
                parent.getFabricType(),
                parent,
                parent.getIntakeDate(),
                parent.getSupplier() == null ? null : parent.getSupplier().getId(),
                request.pricePerUnit() != null ? request.pricePerUnit() : parent.getPricePerUnit(),
                request.note(),
                request.colors());
    }

    /**
     * Records a derby bought on its own, tied to the fabric type rather than to
     * any one purchase of it.
     */
    public FabricIntakeDto recordPurchase(Long fabricTypeId, DerbyPurchaseRequest request) {
        FabricType type = requireType(fabricTypeId);
        FabricIntake latest = latestRegularIntake(fabricTypeId);

        return record(
                type,
                null,
                request.intakeDate(),
                request.supplierId() != null
                        ? request.supplierId()
                        : (latest == null || latest.getSupplier() == null
                                ? null : latest.getSupplier().getId()),
                request.pricePerUnit() != null
                        ? request.pricePerUnit()
                        : (latest == null ? null : latest.getPricePerUnit()),
                request.note(),
                request.colors());
    }

    /**
     * The one place a derby batch is written.
     *
     * <p>The colours become an ordinary derby purchase: one roll per colour,
     * weighing what was entered for it. Derby is bought by weight, not by the
     * roll, but stock is drawn on by the roll — one per colour is the count that
     * lets each colour be picked up and cut on its own.
     */
    private FabricIntakeDto record(
            FabricType type,
            FabricIntake parent,
            LocalDate date,
            Long supplierId,
            BigDecimal price,
            String note,
            List<DerbyColorRequest> colors) {

        // Created on demand: most fabric has a derby, and making someone open an
        // empty pool first was a step that only ever stood in the way.
        poolFor(type);

        BigDecimal total = colors.stream()
                .map(DerbyColorRequest::quantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        FabricIntakeDto batch = intakeService.create(new FabricIntakeRequest(
                type.getId(), true, supplierId, date, colors.size(), total, price, note));

        if (parent != null) {
            intakes.findById(batch.id()).ifPresent(saved -> saved.setParentIntake(parent));
        }

        for (DerbyColorRequest color : colors) {
            intakeService.setColorBreakdown(batch.id(),
                    new FabricIntakeColorRequest(color.fabricColorId(), 1, color.quantity()));
        }

        return intakeService.get(batch.id());
    }

    /** The fabric type's derby pool, created the first time one is needed. */
    private Derby poolFor(FabricType type) {
        return derbies.findByFabricTypeId(type.getId()).orElseGet(() -> {
            Derby derby = new Derby();
            derby.setFabricType(type);
            derby.setNote("دربي " + type.getNameAr());
            return derbies.save(derby);
        });
    }

    public DerbyDto update(Long id, DerbyNoteRequest request) {
        Derby derby = require(id);
        derby.setNote(request.note());
        return DerbyDto.from(derby);
    }

    public void delete(Long id) {
        Derby derby = require(id);
        if (intakes.existsByDerbyId(id)) {
            throw new BusinessRuleException("derby_has_stock",
                    "This derby has stock recorded against it and cannot be removed");
        }
        derbies.delete(derby);
    }

    /** Null when the fabric has never been bought as regular stock. */
    private FabricIntake latestRegularIntake(Long fabricTypeId) {
        return intakes.latestRegular(fabricTypeId, PageRequest.of(0, 1)).stream()
                .findFirst()
                .orElse(null);
    }

    private Derby require(Long id) {
        return derbies.findById(id).orElseThrow(() -> NotFoundException.of("Derby", id));
    }

    private FabricType requireType(Long id) {
        return types.findById(id).orElseThrow(() -> NotFoundException.of("Fabric type", id));
    }
}
