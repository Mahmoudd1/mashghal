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
import jakarta.validation.constraints.Size;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A fabric type's derby pool.
 *
 * <p>One per fabric type at most. Creating it is a one-off; adding more derby
 * fabric afterwards is an intake against this record, not a second derby.
 *
 * <p>A derby is bought as a set of colours with a weight each, not as a roll
 * count, so that is what the form asks for. Underneath it is still an ordinary
 * dated purchase against this pool — which is what makes derby stock visible to
 * the cuts, the remaining report and the waste figures without any of them
 * needing to know a derby exists.
 */
@Service
@Transactional
public class DerbyService {

    /**
     * @param supplierId    null falls back to whoever supplied the fabric last
     * @param pricePerUnit  null falls back to what the fabric last cost
     * @param colors        the opening stock; a derby is created with fabric in it
     */
    public record DerbyRequest(
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
     * Creates the pool and the fabric that is in it, in one go.
     *
     * <p>The colours are turned into an ordinary derby purchase: one roll per
     * colour, weighing what was entered for it. A derby is drawn on by the roll
     * like any other stock, so it needs a roll count even though nobody counts
     * derby in rolls — one per colour is the count that lets each colour be
     * picked up and cut separately.
     */
    public DerbyDto create(Long fabricTypeId, DerbyRequest request) {
        FabricType type = requireType(fabricTypeId);

        if (derbies.existsByFabricTypeId(fabricTypeId)) {
            throw new BusinessRuleException("derby_already_exists",
                    "'%s' already has a derby; add stock to it instead of creating another"
                            .formatted(type.getNameAr()));
        }

        Derby derby = new Derby();
        derby.setFabricType(type);
        derby.setNote(request.note());
        derbies.save(derby);

        // Inherited server-side rather than trusted from the form, so a user who
        // may not see prices still creates a derby that carries the right one.
        FabricIntake latest = latestRegularIntake(fabricTypeId);
        Long supplierId = request.supplierId() != null
                ? request.supplierId()
                : (latest == null || latest.getSupplier() == null ? null : latest.getSupplier().getId());
        BigDecimal price = request.pricePerUnit() != null
                ? request.pricePerUnit()
                : (latest == null ? null : latest.getPricePerUnit());

        BigDecimal total = request.colors().stream()
                .map(DerbyColorRequest::quantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        FabricIntakeDto batch = intakeService.create(new FabricIntakeRequest(
                fabricTypeId,
                true,
                supplierId,
                LocalDate.now(),
                request.colors().size(),
                total,
                price,
                request.note()));

        for (DerbyColorRequest color : request.colors()) {
            intakeService.setColorBreakdown(batch.id(),
                    new FabricIntakeColorRequest(color.fabricColorId(), 1, color.quantity()));
        }

        return DerbyDto.from(derby);
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
