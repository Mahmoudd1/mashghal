package com.apparel.tracking.production.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.apparel.tracking.common.exception.BusinessRuleException;
import com.apparel.tracking.common.exception.NotFoundException;
import com.apparel.tracking.production.domain.Model;
import com.apparel.tracking.production.dto.BranchQuantityDto;
import com.apparel.tracking.production.dto.CutModelAllocationDto;
import com.apparel.tracking.production.dto.ModelCutsDto;
import com.apparel.tracking.fabric.domain.FabricUnit;
import com.apparel.tracking.production.dto.ModelDto;
import com.apparel.tracking.production.domain.CutType;
import com.apparel.tracking.production.dto.ModelFabricUsageDto;
import com.apparel.tracking.production.dto.ModelRequest;
import com.apparel.tracking.production.repository.CutModelAllocationRepository;
import com.apparel.tracking.production.repository.CutModelSizeRepository;
import com.apparel.tracking.production.repository.CutRollRepository;
import com.apparel.tracking.production.repository.ModelRepository;
import com.apparel.tracking.reference.repository.BranchRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Models, plus the quantities derived from their cut allocations.
 *
 * <p>There is no stored planned quantity: every figure here is summed from
 * {@code cut_model_allocation}, so the plan can never drift from the cuts that
 * actually produced the pieces.
 */
@Service
@Transactional
public class ModelService {

    private final ModelRepository models;
    private final CutModelAllocationRepository allocations;
    private final BranchRepository branches;
    private final CutRollRepository cutRolls;
    private final CutModelSizeRepository cutModelSizes;

    public ModelService(
            ModelRepository models,
            CutModelAllocationRepository allocations,
            BranchRepository branches,
            CutRollRepository cutRolls,
            CutModelSizeRepository cutModelSizes) {
        this.models = models;
        this.allocations = allocations;
        this.branches = branches;
        this.cutRolls = cutRolls;
        this.cutModelSizes = cutModelSizes;
    }

    @Transactional(readOnly = true)
    public List<ModelDto> list() {
        List<Model> found = models.findAllByOrderByModelNumberAsc();
        // Two grouped queries for the whole list rather than two per model.
        Map<Long, List<BranchQuantityDto>> plans = plansByModel(null);
        Map<Long, Long> mainCutCounts = mainCutCounts(null);

        return found.stream()
                .map(model -> ModelDto.of(
                        model,
                        plans.getOrDefault(model.getId(), List.of()),
                        mainCutCounts.getOrDefault(model.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public ModelDto get(Long id) {
        Model model = require(id);
        return ModelDto.of(
                model,
                plansByModel(id).getOrDefault(id, List.of()),
                mainCutCounts(id).getOrDefault(id, 0L));
    }

    /** Every cut that fed this model — the "which cuts made this model" direction. */
    @Transactional(readOnly = true)
    public ModelCutsDto cutsFeeding(Long modelId) {
        Model model = require(modelId);
        List<CutModelAllocationDto> rows = allocations.findByModel(modelId).stream()
                .map(CutModelAllocationDto::from)
                .toList();
        long mainCuts = mainCutCounts(modelId).getOrDefault(modelId, 0L);
        return new ModelCutsDto(model.getId(), model.getModelNumber(), mainCuts, mainCuts > 1, rows);
    }

    /**
     * How much of each fabric type goes into one piece of each model.
     *
     * <p>A cut typically makes several models from the same fabric, so its
     * consumption is split between them in proportion to the pieces each takes —
     * pieces-per-layer being the only ratio the data actually carries. Exact when
     * the models share a marker; an approximation when their pieces differ
     * greatly in size, which is worth knowing before costing on it.
     */
    @Transactional(readOnly = true)
    public List<ModelFabricUsageDto> fabricUsagePerPiece(Long modelId) {
        Map<Long, Integer> layersByCut = new HashMap<>();
        for (Object[] row : cutRolls.layersByCut()) {
            layersByCut.put((Long) row[0], ((Number) row[1]).intValue());
        }

        // cut -> model -> pieces per layer
        Map<Long, Map<Long, Integer>> perLayerByCut = new HashMap<>();
        Map<Long, String[]> modelNames = new HashMap<>();
        for (Object[] row : cutModelSizes.piecesPerLayerByCutAndModel()) {
            perLayerByCut.computeIfAbsent((Long) row[0], key -> new LinkedHashMap<>())
                    .put((Long) row[1], ((Number) row[4]).intValue());
            modelNames.putIfAbsent((Long) row[1], new String[] {(String) row[2], (String) row[3]});
        }

        // Cut type joins the key so the main run is read apart from the secondary
        // and derby runs that add to the same garment.
        record Key(Long modelId, Long fabricTypeId, CutType cutType) {}
        Map<Key, BigDecimal> weight = new LinkedHashMap<>();
        Map<Key, Long> pieces = new LinkedHashMap<>();
        Map<Key, Set<Long>> cutsSeen = new LinkedHashMap<>();
        Map<Long, Object[]> fabricNames = new HashMap<>();

        for (Object[] row : cutRolls.consumptionByCutAndFabricType()) {
            Long cutId = (Long) row[0];
            CutType cutType = (CutType) row[1];
            Long fabricTypeId = (Long) row[2];
            BigDecimal consumed = (BigDecimal) row[5];
            fabricNames.putIfAbsent(fabricTypeId, new Object[] {row[3], row[4]});

            Map<Long, Integer> perLayer = perLayerByCut.get(cutId);
            int layers = layersByCut.getOrDefault(cutId, 0);
            if (perLayer == null || perLayer.isEmpty() || layers == 0) {
                continue;
            }

            int totalPerLayer = perLayer.values().stream().mapToInt(Integer::intValue).sum();
            for (var entry : perLayer.entrySet()) {
                if (modelId != null && !modelId.equals(entry.getKey())) {
                    continue;
                }
                Key key = new Key(entry.getKey(), fabricTypeId, cutType);

                // This model's share of the cut, by pieces.
                BigDecimal share = BigDecimal.valueOf(entry.getValue())
                        .divide(BigDecimal.valueOf(totalPerLayer), 6, RoundingMode.HALF_UP);

                weight.merge(key, consumed.multiply(share), BigDecimal::add);
                pieces.merge(key, (long) layers * entry.getValue(), Long::sum);
                cutsSeen.computeIfAbsent(key, k -> new java.util.HashSet<>()).add(cutId);
            }
        }

        List<ModelFabricUsageDto> rows = new ArrayList<>();
        for (var entry : weight.entrySet()) {
            Key key = entry.getKey();
            String[] model = modelNames.get(key.modelId());
            Object[] fabric = fabricNames.get(key.fabricTypeId());
            if (model == null || fabric == null) {
                continue;
            }
            rows.add(ModelFabricUsageDto.of(
                    key.modelId(), model[0], model[1],
                    key.fabricTypeId(), (String) fabric[0], (FabricUnit) fabric[1],
                    key.cutType(),
                    cutsSeen.get(key).size(),
                    pieces.getOrDefault(key, 0L),
                    entry.getValue().setScale(3, RoundingMode.HALF_UP)));
        }
        // Model, then fabric, then MAIN before SECONDARY before DERBY.
        rows.sort((a, b) -> {
            int byModel = a.modelNumber().compareTo(b.modelNumber());
            if (byModel != 0) {
                return byModel;
            }
            int byFabric = a.fabricTypeNameAr().compareTo(b.fabricTypeNameAr());
            return byFabric != 0 ? byFabric : a.cutType().compareTo(b.cutType());
        });
        return rows;
    }

    public ModelDto create(ModelRequest request) {
        if (models.existsByModelNumberIgnoreCase(request.modelNumber())) {
            throw new BusinessRuleException("model_number_taken",
                    "Model number '%s' is already in use".formatted(request.modelNumber()));
        }
        Model model = new Model();
        apply(model, request);
        return ModelDto.of(models.save(model), List.of(), 0L);
    }

    public ModelDto update(Long id, ModelRequest request) {
        Model model = require(id);
        if (!model.getModelNumber().equalsIgnoreCase(request.modelNumber())
                && models.existsByModelNumberIgnoreCase(request.modelNumber())) {
            throw new BusinessRuleException("model_number_taken",
                    "Model number '%s' is already in use".formatted(request.modelNumber()));
        }
        apply(model, request);
        return ModelDto.of(
                model,
                plansByModel(id).getOrDefault(id, List.of()),
                mainCutCounts(id).getOrDefault(id, 0L));
    }

    public void delete(Long id) {
        Model model = require(id);
        if (allocations.existsByModelId(id)) {
            throw new BusinessRuleException("model_has_allocations",
                    "This model has pieces allocated from a cut; deactivate it instead of deleting");
        }
        models.delete(model);
    }

    private void apply(Model model, ModelRequest request) {
        model.setModelNumber(request.modelNumber());
        model.setNameAr(request.nameAr());
        model.setNameEn(request.nameEn());
        model.setNote(request.note());
        model.setSewingBranch(request.sewingBranchId() == null
                ? null
                : branches.findById(request.sewingBranchId())
                        .orElseThrow(() -> NotFoundException.of("Branch", request.sewingBranchId())));
        if (request.active() != null) {
            model.setActive(request.active());
        }
    }

    private Map<Long, List<BranchQuantityDto>> plansByModel(Long modelId) {
        Map<Long, List<BranchQuantityDto>> result = new HashMap<>();
        for (var row : allocations.plannedByModelAndBranch(modelId)) {
            result.computeIfAbsent(row.modelId(), key -> new ArrayList<>())
                    .add(new BranchQuantityDto(
                            row.branchId(), row.branchCode(), row.branchNameAr(), row.branchNameEn(),
                            row.plannedQuantity()));
        }
        return result;
    }

    private Map<Long, Long> mainCutCounts(Long modelId) {
        Map<Long, Long> result = new HashMap<>();
        for (Object[] row : allocations.mainCutCountsByModel(modelId)) {
            result.put((Long) row[0], (Long) row[1]);
        }
        return result;
    }

    private Model require(Long id) {
        return models.findById(id).orElseThrow(() -> NotFoundException.of("Model", id));
    }
}
