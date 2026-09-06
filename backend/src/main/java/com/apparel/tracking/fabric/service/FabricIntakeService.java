package com.apparel.tracking.fabric.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.apparel.tracking.common.exception.BusinessRuleException;
import com.apparel.tracking.common.exception.NotFoundException;
import com.apparel.tracking.common.security.PricePolicy;
import com.apparel.tracking.fabric.domain.Derby;
import com.apparel.tracking.fabric.domain.FabricColor;
import com.apparel.tracking.fabric.domain.FabricIntake;
import com.apparel.tracking.fabric.domain.FabricIntakeColor;
import com.apparel.tracking.fabric.domain.FabricPool;
import com.apparel.tracking.fabric.domain.RemainingGrouping;
import com.apparel.tracking.fabric.domain.FabricType;
import com.apparel.tracking.fabric.domain.FabricUnit;
import com.apparel.tracking.fabric.dto.ColorRollCountDto;
import com.apparel.tracking.fabric.dto.ColorStockDto;
import com.apparel.tracking.fabric.dto.OpenRollRowDto;
import com.apparel.tracking.fabric.dto.FabricIntakeColorRequest;
import com.apparel.tracking.fabric.dto.FabricIntakeDto;
import com.apparel.tracking.fabric.dto.FabricIntakeRequest;
import com.apparel.tracking.fabric.dto.FabricStockDto;
import com.apparel.tracking.fabric.dto.IntakeRemainingRowDto;
import com.apparel.tracking.fabric.dto.RemainingRowDto;
import com.apparel.tracking.fabric.repository.DerbyRepository;
import com.apparel.tracking.fabric.repository.FabricColorRepository;
import com.apparel.tracking.fabric.repository.FabricIntakeColorRepository;
import com.apparel.tracking.fabric.repository.FabricIntakeRepository;
import com.apparel.tracking.fabric.repository.FabricTypeRepository;
import com.apparel.tracking.supplier.domain.Supplier;
import com.apparel.tracking.supplier.repository.SupplierRepository;
import com.apparel.tracking.fabric.repository.FabricRollRepository;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fabric intake: aggregate purchases first, colour breakdown later.
 *
 * <p>The breakdown is deliberately soft. Assigning fewer rolls to colours than
 * the batch holds is normal — the user learns the colours over time — so the
 * shortfall is reported as {@code unassignedRolls} and never blocks a save. What
 * <em>is</em> enforced is that a colour belongs to the batch's fabric type and
 * that no batch gives out more than it holds.
 */
@Service
@Transactional
public class FabricIntakeService {

    private final FabricIntakeRepository intakes;
    private final FabricIntakeColorRepository breakdownRows;
    private final FabricTypeRepository types;
    private final FabricColorRepository colors;
    private final DerbyRepository derbies;
    private final FabricRollRepository fabricRolls;
    private final SupplierRepository suppliers;
    private final PricePolicy pricePolicy;

    public FabricIntakeService(
            FabricIntakeRepository intakes,
            FabricIntakeColorRepository breakdownRows,
            FabricTypeRepository types,
            FabricColorRepository colors,
            DerbyRepository derbies,
            FabricRollRepository fabricRolls,
            SupplierRepository suppliers,
            PricePolicy pricePolicy) {
        this.intakes = intakes;
        this.breakdownRows = breakdownRows;
        this.types = types;
        this.colors = colors;
        this.derbies = derbies;
        this.fabricRolls = fabricRolls;
        this.suppliers = suppliers;
        this.pricePolicy = pricePolicy;
    }

    @Transactional(readOnly = true)
    public Page<FabricIntakeDto> search(Long fabricTypeId, Boolean derbyOnly, boolean inStockOnly, Pageable pageable) {
        return intakes.search(fabricTypeId, derbyOnly, inStockOnly, pageable).map(this::visible);
    }

    @Transactional(readOnly = true)
    public FabricIntakeDto get(Long id) {
        return visible(require(id));
    }

    @Transactional(readOnly = true)
    public List<IntakeRemainingRowDto> remainingByDate(Long fabricTypeId, boolean inStockOnly) {
        List<IntakeRemainingRowDto> rows = intakes.remainingByDate(fabricTypeId, inStockOnly);
        return pricePolicy.canSeePrices()
                ? rows
                : rows.stream().map(IntakeRemainingRowDto::withoutPrices).toList();
    }

    public FabricIntakeDto create(FabricIntakeRequest request) {
        FabricType type = requireType(request.fabricTypeId());

        FabricIntake intake = new FabricIntake();
        intake.setFabricType(type);
        intake.setDerby(request.derbyPool() ? requireDerby(type) : null);
        applyEditableFields(intake, request);
        intake.setConsumedQuantity(BigDecimal.ZERO);

        return visible(intakes.save(intake));
    }

    /**
     * Corrects a recorded purchase. Totals may not drop below what cuts have
     * already drawn from the batch, and a batch that has been drawn on cannot
     * change pool — its fabric is already committed to one side.
     */
    public FabricIntakeDto update(Long id, FabricIntakeRequest request) {
        FabricIntake intake = require(id);
        boolean consumed = intake.getConsumedRolls() > 0;

        boolean wantsDerby = request.derbyPool();
        if (wantsDerby != intake.isDerbyPool()) {
            if (consumed) {
                throw new BusinessRuleException("intake_pool_locked",
                        "Fabric has already been cut from this batch, so it cannot move between the regular and derby pools");
            }
            intake.setDerby(wantsDerby ? requireDerby(intake.getFabricType()) : null);
        }

        if (request.totalRolls() < intake.getConsumedRolls()) {
            throw new BusinessRuleException("intake_below_consumed_rolls",
                    "%d rolls have already been cut from this batch, so the total cannot drop to %d"
                            .formatted(intake.getConsumedRolls(), request.totalRolls()));
        }
        // Wasted fabric is as gone as cut fabric, so the total has to cover both.
        BigDecimal accountedFor = intake.getConsumedQuantity().add(intake.getWastedQuantity());
        if (request.totalQuantity().compareTo(accountedFor) < 0) {
            throw new BusinessRuleException("intake_below_consumed_quantity",
                    "%s has already left this batch, so the total cannot drop to %s"
                            .formatted(accountedFor, request.totalQuantity()));
        }

        applyEditableFields(intake, request);
        return visible(intake);
    }

    public void delete(Long id) {
        FabricIntake intake = require(id);
        if (intake.getConsumedRolls() > 0
                || intake.getConsumedQuantity().signum() > 0
                || intake.getWastedQuantity().signum() > 0) {
            throw new BusinessRuleException("intake_already_allocated",
                    "Fabric from this batch has been cut and the batch can no longer be deleted");
        }
        intakes.delete(intake);
    }

    // --- colour breakdown ----------------------------------------------------

    /**
     * Adds or adjusts one colour's share of a batch. Repeating a colour updates
     * the existing row rather than duplicating it.
     */
    public FabricIntakeDto setColorBreakdown(Long intakeId, FabricIntakeColorRequest request) {
        FabricIntake intake = require(intakeId);
        FabricColor color = colors.findById(request.fabricColorId())
                .orElseThrow(() -> NotFoundException.of("Fabric colour", request.fabricColorId()));

        if (!color.getFabricType().getId().equals(intake.getFabricType().getId())) {
            throw new BusinessRuleException("color_wrong_fabric_type",
                    "'%s' is not a colour of this batch's fabric type".formatted(color.getNameAr()));
        }

        FabricIntakeColor row = breakdownRows.findByIntakeIdAndColorId(intakeId, color.getId())
                .orElseGet(() -> {
                    FabricIntakeColor created = new FabricIntakeColor();
                    created.setIntake(intake);
                    created.setColor(color);
                    intake.getColorBreakdown().add(created);
                    return created;
                });

        row.setRollCount(request.rollCount());
        row.setQuantity(request.quantity());
        breakdownRows.save(row);

        // No check that the colours add up: a partial breakdown is expected and
        // the shortfall travels back as unassignedRolls for the UI to surface.
        return visible(intake);
    }

    public FabricIntakeDto removeColorBreakdown(Long intakeId, Long colorId) {
        FabricIntake intake = require(intakeId);
        FabricIntakeColor row = breakdownRows.findByIntakeIdAndColorId(intakeId, colorId)
                .orElseThrow(() -> new NotFoundException("This batch has no breakdown for that colour"));

        intake.getColorBreakdown().remove(row);
        breakdownRows.delete(row);
        return visible(intake);
    }

    /** How many rolls are sitting part-used right now, per fabric type and colour. */
    @Transactional(readOnly = true)
    public List<OpenRollRowDto> openRolls(Long fabricTypeId) {
        return fabricRolls.openRollSummary(fabricTypeId).stream()
                .map(row -> new OpenRollRowDto(
                        (Long) row[0], (String) row[1], (Long) row[2], (String) row[3],
                        ((Number) row[4]).longValue(), (BigDecimal) row[5]))
                .toList();
    }

    /** Roll count per colour, for a fabric type overall or one intake date. */
    @Transactional(readOnly = true)
    public List<ColorRollCountDto> rollCountByColor(Long fabricTypeId, LocalDate intakeDate, boolean openOnly) {
        return fabricRolls.rollCountByColor(fabricTypeId, intakeDate, openOnly).stream()
                .map(row -> new ColorRollCountDto(
                        (Long) row[0], (String) row[1], ((Number) row[2]).longValue(), (BigDecimal) row[3]))
                .toList();
    }

    /**
     * How much of each fabric is left, grouped as asked: overall, by the date it
     * came in, or by who supplied it.
     */
    @Transactional(readOnly = true)
    public List<RemainingRowDto> remaining(Long fabricTypeId, RemainingGrouping grouping) {
        return switch (grouping) {
            case TOTAL -> intakes.remainingByType(fabricTypeId).stream()
                    .map(r -> row(r, null, null, null, 4))
                    .toList();
            case DATE -> intakes.remainingByDateGrouped(fabricTypeId).stream()
                    .map(r -> row(r, (LocalDate) r[4], null, null, 5))
                    .toList();
            case SUPPLIER -> intakes.remainingBySupplier(fabricTypeId).stream()
                    .map(r -> row(r, null, (Long) r[4], (String) r[5], 6))
                    .toList();
        };
    }

    /**
     * The three queries share a head (type, unit) and a tail (counts); only the
     * grouping columns in between differ, so {@code offset} says where the tail
     * starts.
     */
    private RemainingRowDto row(Object[] r, LocalDate date, Long supplierId, String supplierName, int offset) {
        return new RemainingRowDto(
                (Long) r[0], (String) r[1], (String) r[2], (FabricUnit) r[3],
                date, supplierId, supplierName,
                ((Number) r[offset]).longValue(),
                ((Number) r[offset + 1]).longValue(),
                ((Number) r[offset + 2]).longValue(),
                (BigDecimal) r[offset + 3],
                (BigDecimal) r[offset + 4]);
    }

    // --- stock report --------------------------------------------------------

    /** Pool totals per fabric type, with the indicative colour breakdown beneath. */
    @Transactional(readOnly = true)
    public List<FabricStockDto> stock(Long fabricTypeId) {
        Map<Long, FabricType> typesById = new HashMap<>();
        (fabricTypeId == null ? types.findAllByOrderByNameArAsc() : List.of(requireType(fabricTypeId)))
                .forEach(type -> typesById.put(type.getId(), type));

        Map<String, List<ColorStockDto>> colorRows = colorRows(fabricTypeId);
        List<FabricStockDto> result = new ArrayList<>();

        for (Object[] row : intakes.poolTotals(fabricTypeId)) {
            Long typeId = (Long) row[0];
            boolean derby = (Boolean) row[1];
            FabricType type = typesById.get(typeId);
            if (type == null) {
                continue;
            }

            List<ColorStockDto> colorStock = colorRows.getOrDefault(key(typeId, derby), List.of());
            int assigned = colorStock.stream().mapToInt(ColorStockDto::assignedRolls).sum();
            int totalRolls = ((Number) row[3]).intValue();

            result.add(new FabricStockDto(
                    typeId, type.getNameAr(), type.getNameEn(), type.getUnit(),
                    derby ? FabricPool.DERBY : FabricPool.REGULAR,
                    ((Number) row[2]).intValue(),
                    totalRolls,
                    ((Number) row[4]).intValue(),
                    (BigDecimal) row[5],
                    (BigDecimal) row[6],
                    (BigDecimal) row[7],
                    colorStock,
                    Math.max(0, totalRolls - assigned)));
        }

        result.sort((a, b) -> {
            int byName = a.fabricTypeNameAr().compareTo(b.fabricTypeNameAr());
            return byName != 0 ? byName : a.pool().compareTo(b.pool());
        });
        return pricePolicy.canSeePrices()
                ? result
                : result.stream().map(FabricStockDto::withoutPrices).toList();
    }

    private Map<String, List<ColorStockDto>> colorRows(Long fabricTypeId) {
        // Consumption attributed to a colour comes off the rolls themselves.
        Map<String, int[]> consumedRolls = new HashMap<>();
        Map<String, BigDecimal> consumedQuantity = new HashMap<>();
        for (Object[] row : fabricRolls.colorConsumption(fabricTypeId)) {
            String key = key((Long) row[0], (Boolean) row[1]) + "#" + row[2];
            consumedRolls.put(key, new int[] {((Number) row[3]).intValue()});
            consumedQuantity.put(key, (BigDecimal) row[4]);
        }

        Map<String, List<ColorStockDto>> result = new LinkedHashMap<>();
        for (Object[] row : intakes.colorAssignments(fabricTypeId)) {
            String poolKey = key((Long) row[0], (Boolean) row[1]);
            String colorKey = poolKey + "#" + row[2];

            result.computeIfAbsent(poolKey, key -> new ArrayList<>())
                    .add(new ColorStockDto(
                            (Long) row[2], (String) row[3], (String) row[4],
                            ((Number) row[5]).intValue(),
                            (BigDecimal) row[6],
                            consumedRolls.getOrDefault(colorKey, new int[] {0})[0],
                            consumedQuantity.getOrDefault(colorKey, BigDecimal.ZERO)));
        }
        return result;
    }

    private static String key(Long typeId, boolean derby) {
        return typeId + (derby ? ":DERBY" : ":REGULAR");
    }

    // --- helpers -------------------------------------------------------------

    /**
     * Every intake leaving this service goes through here, so a new read path
     * cannot forget to strip the money — it has to opt in to the raw DTO.
     */
    private FabricIntakeDto visible(FabricIntake intake) {
        FabricIntakeDto dto = FabricIntakeDto.from(intake);
        return pricePolicy.canSeePrices() ? dto : dto.withoutPrices();
    }

    private void applyEditableFields(FabricIntake intake, FabricIntakeRequest request) {
        intake.setSupplier(resolveSupplier(request.supplierId()));
        intake.setIntakeDate(request.intakeDate());
        intake.setTotalRolls(request.totalRolls());
        intake.setTotalQuantity(request.totalQuantity());
        intake.setPricePerUnit(request.pricePerUnit());
        intake.setNote(request.note());
    }

    private Supplier resolveSupplier(Long id) {
        return id == null
                ? null
                : suppliers.findById(id).orElseThrow(() -> NotFoundException.of("Supplier", id));
    }

    private Derby requireDerby(FabricType type) {
        return derbies.findByFabricTypeId(type.getId())
                .orElseThrow(() -> new BusinessRuleException("derby_missing",
                        "'%s' has no derby yet; create one before adding derby stock"
                                .formatted(type.getNameAr())));
    }

    private FabricIntake require(Long id) {
        return intakes.findById(id).orElseThrow(() -> NotFoundException.of("Fabric intake", id));
    }

    private FabricType requireType(Long id) {
        return types.findById(id).orElseThrow(() -> NotFoundException.of("Fabric type", id));
    }
}
