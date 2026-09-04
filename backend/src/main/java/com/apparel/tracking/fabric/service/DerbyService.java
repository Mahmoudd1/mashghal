package com.apparel.tracking.fabric.service;

import java.util.List;

import com.apparel.tracking.common.exception.BusinessRuleException;
import com.apparel.tracking.common.exception.NotFoundException;
import com.apparel.tracking.fabric.domain.Derby;
import com.apparel.tracking.fabric.domain.FabricType;
import com.apparel.tracking.fabric.dto.DerbyDto;
import com.apparel.tracking.fabric.repository.DerbyRepository;
import com.apparel.tracking.fabric.repository.FabricIntakeRepository;
import com.apparel.tracking.fabric.repository.FabricTypeRepository;

import jakarta.validation.constraints.Size;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A fabric type's derby pool.
 *
 * <p>One per fabric type at most. Creating it is a one-off; adding more derby
 * fabric afterwards is an intake against this record, not a second derby.
 */
@Service
@Transactional
public class DerbyService {

    public record DerbyRequest(@Size(max = 512) String note) {
    }

    private final DerbyRepository derbies;
    private final FabricTypeRepository types;
    private final FabricIntakeRepository intakes;

    public DerbyService(DerbyRepository derbies, FabricTypeRepository types, FabricIntakeRepository intakes) {
        this.derbies = derbies;
        this.types = types;
        this.intakes = intakes;
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

    public DerbyDto create(Long fabricTypeId, DerbyRequest request) {
        FabricType type = types.findById(fabricTypeId)
                .orElseThrow(() -> NotFoundException.of("Fabric type", fabricTypeId));

        if (derbies.existsByFabricTypeId(fabricTypeId)) {
            throw new BusinessRuleException("derby_already_exists",
                    "'%s' already has a derby; add stock to it instead of creating another"
                            .formatted(type.getNameAr()));
        }

        Derby derby = new Derby();
        derby.setFabricType(type);
        derby.setNote(request.note());
        return DerbyDto.from(derbies.save(derby));
    }

    public DerbyDto update(Long id, DerbyRequest request) {
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

    private Derby require(Long id) {
        return derbies.findById(id).orElseThrow(() -> NotFoundException.of("Derby", id));
    }
}
