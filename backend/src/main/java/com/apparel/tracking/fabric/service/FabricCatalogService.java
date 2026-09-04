package com.apparel.tracking.fabric.service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.apparel.tracking.common.exception.BusinessRuleException;
import com.apparel.tracking.common.exception.NotFoundException;
import com.apparel.tracking.fabric.domain.FabricColor;
import com.apparel.tracking.fabric.domain.FabricType;
import com.apparel.tracking.fabric.dto.FabricColorDto;
import com.apparel.tracking.fabric.dto.FabricColorRequest;
import com.apparel.tracking.fabric.dto.FabricTypeDto;
import com.apparel.tracking.fabric.dto.FabricTypeRequest;
import com.apparel.tracking.fabric.repository.FabricColorRepository;
import com.apparel.tracking.fabric.repository.DerbyRepository;
import com.apparel.tracking.fabric.repository.FabricIntakeColorRepository;
import com.apparel.tracking.fabric.repository.FabricIntakeRepository;
import com.apparel.tracking.fabric.repository.FabricTypeRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Fabric master data: types and the colours available under each type. */
@Service
@Transactional
public class FabricCatalogService {

    private final FabricTypeRepository types;
    private final FabricColorRepository colors;
    private final FabricIntakeRepository intakes;
    private final FabricIntakeColorRepository breakdownRows;
    private final DerbyRepository derbies;

    public FabricCatalogService(
            FabricTypeRepository types,
            FabricColorRepository colors,
            FabricIntakeRepository intakes,
            FabricIntakeColorRepository breakdownRows,
            DerbyRepository derbies) {
        this.types = types;
        this.colors = colors;
        this.intakes = intakes;
        this.breakdownRows = breakdownRows;
        this.derbies = derbies;
    }

    @Transactional(readOnly = true)
    public List<FabricTypeDto> listTypes(boolean activeOnly) {
        List<FabricType> found = activeOnly ? types.findAllByActiveTrueOrderByNameArAsc() : types.findAllByOrderByNameArAsc();
        Set<Long> withDerby = derbies.findAllByFabricTypeIdIn(found.stream().map(FabricType::getId).toList()).stream()
                .map(derby -> derby.getFabricType().getId())
                .collect(Collectors.toSet());
        return found.stream().map(type -> FabricTypeDto.from(type, withDerby.contains(type.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public FabricTypeDto getType(Long id) {
        return FabricTypeDto.from(requireType(id), derbies.existsByFabricTypeId(id));
    }

    public FabricTypeDto createType(FabricTypeRequest request) {
        if (types.existsByNameArIgnoreCase(request.nameAr())) {
            throw new BusinessRuleException("fabric_type_name_taken",
                    "A fabric type named '%s' already exists".formatted(request.nameAr()));
        }
        FabricType type = new FabricType();
        type.setNameAr(request.nameAr());
        type.setNameEn(request.nameEn());
        type.setUnit(request.unit());
        type.setActive(request.active() == null || request.active());
        return FabricTypeDto.from(types.save(type), false);
    }

    public FabricTypeDto updateType(Long id, FabricTypeRequest request) {
        FabricType type = requireType(id);

        boolean renamed = !type.getNameAr().equalsIgnoreCase(request.nameAr());
        if (renamed && types.existsByNameArIgnoreCase(request.nameAr())) {
            throw new BusinessRuleException("fabric_type_name_taken",
                    "A fabric type named '%s' already exists".formatted(request.nameAr()));
        }

        // Every batch's quantity and price are recorded in the type's unit. Switching
        // the unit after the fact would silently reinterpret those numbers.
        if (type.getUnit() != request.unit() && intakes.existsByFabricTypeId(id)) {
            throw new BusinessRuleException("fabric_type_unit_locked",
                    "The unit cannot change once stock has been recorded for this fabric type");
        }

        type.setNameAr(request.nameAr());
        type.setNameEn(request.nameEn());
        type.setUnit(request.unit());
        if (request.active() != null) {
            type.setActive(request.active());
        }
        return FabricTypeDto.from(type, derbies.existsByFabricTypeId(id));
    }

    public void deleteType(Long id) {
        FabricType type = requireType(id);
        if (intakes.existsByFabricTypeId(id)) {
            throw new BusinessRuleException("fabric_type_in_use",
                    "This fabric type has stock recorded against it; deactivate it instead of deleting");
        }
        if (derbies.existsByFabricTypeId(id)) {
            throw new BusinessRuleException("fabric_type_has_derby",
                    "Remove this fabric type's derby before deleting the type");
        }
        types.delete(type);
    }

    @Transactional(readOnly = true)
    public List<FabricColorDto> listColors(Long fabricTypeId) {
        requireType(fabricTypeId);
        return colors.findAllByFabricTypeIdOrderByNameArAsc(fabricTypeId).stream()
                .map(FabricColorDto::from)
                .toList();
    }

    public FabricColorDto addColor(Long fabricTypeId, FabricColorRequest request) {
        FabricType type = requireType(fabricTypeId);
        if (colors.existsByFabricTypeIdAndNameArIgnoreCase(fabricTypeId, request.nameAr())) {
            throw new BusinessRuleException("fabric_color_name_taken",
                    "'%s' is already a colour of this fabric type".formatted(request.nameAr()));
        }
        FabricColor color = new FabricColor();
        color.setFabricType(type);
        color.setNameAr(request.nameAr());
        color.setNameEn(request.nameEn());
        color.setActive(request.active() == null || request.active());
        return FabricColorDto.from(colors.save(color));
    }

    public FabricColorDto updateColor(Long colorId, FabricColorRequest request) {
        FabricColor color = requireColor(colorId);

        boolean renamed = !color.getNameAr().equalsIgnoreCase(request.nameAr());
        if (renamed
                && colors.existsByFabricTypeIdAndNameArIgnoreCase(color.getFabricType().getId(), request.nameAr())) {
            throw new BusinessRuleException("fabric_color_name_taken",
                    "'%s' is already a colour of this fabric type".formatted(request.nameAr()));
        }

        color.setNameAr(request.nameAr());
        color.setNameEn(request.nameEn());
        if (request.active() != null) {
            color.setActive(request.active());
        }
        return FabricColorDto.from(color);
    }

    public void deleteColor(Long colorId) {
        FabricColor color = requireColor(colorId);
        if (breakdownRows.existsByColorId(colorId)) {
            throw new BusinessRuleException("fabric_color_in_use",
                    "This colour appears in a stock breakdown; deactivate it instead of deleting");
        }
        colors.delete(color);
    }

    private FabricType requireType(Long id) {
        return types.findById(id).orElseThrow(() -> NotFoundException.of("Fabric type", id));
    }

    private FabricColor requireColor(Long id) {
        return colors.findById(id).orElseThrow(() -> NotFoundException.of("Fabric colour", id));
    }
}
