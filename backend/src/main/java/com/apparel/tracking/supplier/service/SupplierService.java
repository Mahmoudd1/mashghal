package com.apparel.tracking.supplier.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.apparel.tracking.common.exception.BusinessRuleException;
import com.apparel.tracking.common.exception.NotFoundException;
import com.apparel.tracking.fabric.domain.FabricUnit;
import com.apparel.tracking.fabric.repository.FabricIntakeRepository;
import com.apparel.tracking.supplier.domain.Supplier;
import com.apparel.tracking.supplier.dto.FabricPriceRowDto;
import com.apparel.tracking.supplier.dto.SupplierDto;
import com.apparel.tracking.supplier.dto.SupplierRequest;
import com.apparel.tracking.supplier.repository.SupplierRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Suppliers, and what fabric has cost from them.
 *
 * <p>Average price is weighted by quantity rather than a plain mean of batch
 * prices: a two-tonne purchase and a fifty-kilo purchase say very different
 * things about what a fabric costs, and only the weighted figure reconciles with
 * the value of the stock on hand.
 */
@Service
@Transactional
public class SupplierService {

    private final SupplierRepository suppliers;
    private final FabricIntakeRepository intakes;

    public SupplierService(SupplierRepository suppliers, FabricIntakeRepository intakes) {
        this.suppliers = suppliers;
        this.intakes = intakes;
    }

    @Transactional(readOnly = true)
    public List<SupplierDto> list(boolean activeOnly) {
        List<Supplier> found = activeOnly
                ? suppliers.findAllByActiveTrueOrderByNameArAsc()
                : suppliers.findAllByOrderByNameArAsc();
        return found.stream().map(SupplierDto::from).toList();
    }

    public SupplierDto create(SupplierRequest request) {
        if (suppliers.existsByNameArIgnoreCase(request.nameAr())) {
            throw new BusinessRuleException("supplier_name_taken",
                    "A supplier named '%s' already exists".formatted(request.nameAr()));
        }
        Supplier supplier = new Supplier();
        apply(supplier, request);
        return SupplierDto.from(suppliers.save(supplier));
    }

    public SupplierDto update(Long id, SupplierRequest request) {
        Supplier supplier = require(id);
        if (!supplier.getNameAr().equalsIgnoreCase(request.nameAr())
                && suppliers.existsByNameArIgnoreCase(request.nameAr())) {
            throw new BusinessRuleException("supplier_name_taken",
                    "A supplier named '%s' already exists".formatted(request.nameAr()));
        }
        apply(supplier, request);
        return SupplierDto.from(supplier);
    }

    public void delete(Long id) {
        Supplier supplier = require(id);
        boolean hasPurchases = !intakes.priceTotalsBySupplier(null, id).isEmpty();
        if (hasPurchases) {
            throw new BusinessRuleException("supplier_has_purchases",
                    "This supplier has purchases recorded against them; deactivate instead of deleting");
        }
        suppliers.delete(supplier);
    }

    /**
     * What each fabric has cost.
     *
     * @param bySupplier true to split each fabric by who supplied it — the view
     *                   that answers "which provider is cheaper for cotton"
     */
    @Transactional(readOnly = true)
    public List<FabricPriceRowDto> fabricPrices(Long fabricTypeId, Long supplierId, boolean bySupplier) {
        Map<String, Object[]> latest = latestByKey(fabricTypeId, supplierId);
        List<FabricPriceRowDto> rows = new ArrayList<>();

        if (bySupplier) {
            for (Object[] row : intakes.priceTotalsBySupplier(fabricTypeId, supplierId)) {
                Long typeId = (Long) row[0];
                Long rowSupplierId = (Long) row[4];
                Object[] latestRow = latest.get(key(typeId, rowSupplierId));
                rows.add(FabricPriceRowDto.of(
                        typeId, (String) row[1], (String) row[2], (FabricUnit) row[3],
                        rowSupplierId, (String) row[5],
                        ((Number) row[6]).longValue(), (BigDecimal) row[7], (BigDecimal) row[8],
                        (BigDecimal) row[9], (BigDecimal) row[10],
                        latestRow == null ? null : (BigDecimal) latestRow[3],
                        latestRow == null ? null : (LocalDate) latestRow[2]));
            }
            return rows;
        }

        // Without the supplier split, the latest price is the newest purchase of
        // that fabric from anyone.
        Map<Long, Object[]> latestByType = new HashMap<>();
        for (Object[] row : intakes.latestPrices(fabricTypeId, supplierId)) {
            latestByType.putIfAbsent((Long) row[0], row);
        }

        for (Object[] row : intakes.priceTotals(fabricTypeId)) {
            Long typeId = (Long) row[0];
            Object[] latestRow = latestByType.get(typeId);
            rows.add(FabricPriceRowDto.of(
                    typeId, (String) row[1], (String) row[2], (FabricUnit) row[3],
                    null, null,
                    ((Number) row[4]).longValue(), (BigDecimal) row[5], (BigDecimal) row[6],
                    (BigDecimal) row[7], (BigDecimal) row[8],
                    latestRow == null ? null : (BigDecimal) latestRow[3],
                    latestRow == null ? null : (LocalDate) latestRow[2]));
        }
        return rows;
    }

    /** The query returns newest first, so the first hit per key is the latest. */
    private Map<String, Object[]> latestByKey(Long fabricTypeId, Long supplierId) {
        Map<String, Object[]> latest = new HashMap<>();
        for (Object[] row : intakes.latestPrices(fabricTypeId, supplierId)) {
            latest.putIfAbsent(key((Long) row[0], (Long) row[1]), row);
        }
        return latest;
    }

    private static String key(Long fabricTypeId, Long supplierId) {
        return fabricTypeId + ":" + supplierId;
    }

    private void apply(Supplier supplier, SupplierRequest request) {
        supplier.setNameAr(request.nameAr());
        supplier.setNameEn(request.nameEn());
        supplier.setPhone(request.phone());
        supplier.setNote(request.note());
        if (request.active() != null) {
            supplier.setActive(request.active());
        }
    }

    private Supplier require(Long id) {
        return suppliers.findById(id).orElseThrow(() -> NotFoundException.of("Supplier", id));
    }
}
