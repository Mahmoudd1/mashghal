package com.apparel.tracking.production.service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.apparel.tracking.audit.service.AuditService;
import com.apparel.tracking.common.exception.BusinessRuleException;
import com.apparel.tracking.common.exception.NotFoundException;
import com.apparel.tracking.fabric.domain.FabricType;
import com.apparel.tracking.fabric.repository.FabricTypeRepository;
import com.apparel.tracking.pipeline.service.PipelineService;
import com.apparel.tracking.production.domain.Cut;
import com.apparel.tracking.production.domain.CutModelAllocation;
import com.apparel.tracking.production.domain.CutModelSize;
import com.apparel.tracking.production.domain.CutRoll;
import com.apparel.tracking.production.domain.CutStatus;
import com.apparel.tracking.production.domain.CutType;
import com.apparel.tracking.production.domain.Model;
import com.apparel.tracking.production.dto.CutDto;
import com.apparel.tracking.production.dto.CutModelAllocationDto;
import com.apparel.tracking.production.dto.CutModelAllocationRequest;
import com.apparel.tracking.production.dto.CutRequest;
import com.apparel.tracking.production.dto.CutModelDerivedDto;
import com.apparel.tracking.production.dto.CutModelSizeDto;
import com.apparel.tracking.production.dto.CutModelSizeRequest;
import com.apparel.tracking.production.dto.CutRollDto;
import com.apparel.tracking.production.dto.CutRollRequest;
import com.apparel.tracking.production.repository.CutModelAllocationRepository;
import com.apparel.tracking.production.repository.CutRepository;
import com.apparel.tracking.production.repository.CutModelSizeRepository;
import com.apparel.tracking.production.repository.CutRollRepository;
import com.apparel.tracking.production.repository.ModelRepository;
import com.apparel.tracking.size.domain.GarmentSize;
import com.apparel.tracking.size.repository.GarmentSizeRepository;
import com.apparel.tracking.reference.domain.Branch;
import com.apparel.tracking.reference.repository.BranchRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cutting runs and their two kinds of allocation: pieces out to models, and
 * fabric in from rolls.
 *
 * <p>Deliberately absent: any rule rejecting a second main cut for a model.
 * That case is rare but legitimate, so it is surfaced in reporting rather than
 * blocked. What <em>is</em> enforced is that a cut only accepts allocations
 * while it is OPEN.
 */
@Service
@Transactional
public class CutService {

    private static final String ENTITY = "Cut";

    private final CutRepository cuts;
    private final ModelRepository models;
    private final BranchRepository branches;
    private final FabricTypeRepository fabricTypes;
    private final CutModelAllocationRepository modelAllocations;
    private final CutRollRepository cutRolls;
    private final CutModelSizeRepository cutModelSizes;
    private final CutRollService cutRollService;
    private final GarmentSizeRepository sizes;
    private final AuditService audit;
    private final PipelineService pipeline;

    public CutService(
            CutRepository cuts,
            ModelRepository models,
            BranchRepository branches,
            FabricTypeRepository fabricTypes,
            CutModelAllocationRepository modelAllocations,
            CutRollRepository cutRolls,
            CutModelSizeRepository cutModelSizes,
            CutRollService cutRollService,
            GarmentSizeRepository sizes,
            AuditService audit,
            PipelineService pipeline) {
        this.cuts = cuts;
        this.models = models;
        this.branches = branches;
        this.fabricTypes = fabricTypes;
        this.modelAllocations = modelAllocations;
        this.cutRolls = cutRolls;
        this.cutModelSizes = cutModelSizes;
        this.cutRollService = cutRollService;
        this.sizes = sizes;
        this.audit = audit;
        this.pipeline = pipeline;
    }

    @Transactional(readOnly = true)
    public Page<CutDto> search(CutType cutType, CutStatus status, Long branchId, Long modelId, Pageable pageable) {
        Page<Cut> page = cuts.search(cutType, status, branchId, modelId, pageable);
        List<Long> ids = page.getContent().stream().map(Cut::getId).toList();

        Map<Long, Long> allocated = totalsByCut(ids);
        Map<Long, Object[]> rollTotals = rollTotalsByCut(ids);

        return page.map(cut -> {
            Object[] totals = rollTotals.get(cut.getId());
            int layers = totals == null ? 0 : ((Number) totals[0]).intValue();
            BigDecimal consumed = totals == null ? BigDecimal.ZERO : (BigDecimal) totals[1];
            BigDecimal defect = totals == null ? BigDecimal.ZERO : (BigDecimal) totals[2];
            return CutDto.summary(cut, layers, consumed, defect, allocated.getOrDefault(cut.getId(), 0L));
        });
    }

    @Transactional(readOnly = true)
    public CutDto get(Long id) {
        Cut cut = require(id);
        return detailOf(cut);
    }

    /** Every model this cut fed — the "where did this cut go" direction. */
    @Transactional(readOnly = true)
    public List<CutModelAllocationDto> modelsFedBy(Long cutId) {
        require(cutId);
        return modelAllocationDtos(cutId);
    }

    public CutDto create(CutRequest request) {
        if (cuts.existsByCutNumberIgnoreCase(request.cutNumber())) {
            throw new BusinessRuleException("cut_number_taken",
                    "Cut number '%s' is already in use".formatted(request.cutNumber()));
        }

        Cut cut = new Cut();
        cut.setCutNumber(request.cutNumber());
        cut.setCutType(request.cutType());
        cut.setBranch(requireBranch(request.branchId()));
        cut.setFabricType(resolveFabricType(request.fabricTypeId()));
        cut.setStatus(CutStatus.OPEN);
        applyEditableFields(cut, request);
        cut.assignParent(resolveParent(request));
        // Opening a cut is normally the moment its model comes into being.
        cut.setPrimaryModel(resolvePrimaryModel(request));

        return detailOf(cuts.save(cut));
    }

    public CutDto update(Long id, CutRequest request) {
        Cut cut = require(id);

        if (!cut.getCutNumber().equalsIgnoreCase(request.cutNumber())
                && cuts.existsByCutNumberIgnoreCase(request.cutNumber())) {
            throw new BusinessRuleException("cut_number_taken",
                    "Cut number '%s' is already in use".formatted(request.cutNumber()));
        }

        // Changing the type would invalidate children (for a MAIN cut) or the
        // parent link (for the others), so it is fixed at creation.
        if (cut.getCutType() != request.cutType()) {
            throw new BusinessRuleException("cut_type_immutable",
                    "A cut's type cannot change after it is created");
        }

        cut.setCutNumber(request.cutNumber());
        cut.setBranch(requireBranch(request.branchId()));
        cut.setFabricType(resolveFabricType(request.fabricTypeId()));
        applyEditableFields(cut, request);
        cut.assignParent(resolveParent(request));
        cut.setPrimaryModel(resolvePrimaryModel(request));

        return detailOf(cut);
    }

    public CutDto close(Long id) {
        Cut cut = require(id);
        if (!cut.isOpen()) {
            throw new BusinessRuleException("cut_already_closed", "This cut is already closed");
        }
        for (CutModelDerivedDto row : derivedTotals(cut)) {
            if (!row.balanced()) {
                throw new BusinessRuleException("cut_allocation_unbalanced",
                        ("Model %s yields %d pieces from this cut but %d are allocated to branches; "
                                        + "balance them before closing.")
                                .formatted(row.modelNumber(), row.derivedPieces(), row.allocatedPieces()));
            }
        }

        cut.setStatus(CutStatus.CLOSED);
        audit.record(AuditService.CUT_CLOSED, ENTITY, cut.getId(), cut.getBranch(), null, null);
        return detailOf(cut);
    }

    public CutDto reopen(Long id) {
        Cut cut = require(id);
        if (cut.isOpen()) {
            throw new BusinessRuleException("cut_already_open", "This cut is already open");
        }
        cut.setStatus(CutStatus.OPEN);
        audit.record(AuditService.CUT_REOPENED, ENTITY, cut.getId(), cut.getBranch(), null, null);
        return detailOf(cut);
    }

    public void delete(Long id) {
        Cut cut = require(id);

        if (cut.getCutType() == CutType.MAIN && cuts.existsByParentMainCutId(id)) {
            throw new BusinessRuleException("cut_has_children",
                    "Secondary or derby cuts reference this main cut; delete those first");
        }
        if (!modelAllocations.findByCut(id).isEmpty()) {
            throw new BusinessRuleException("cut_has_model_allocations",
                    "This cut has pieces allocated to models; remove those allocations first");
        }

        // Fabric returns to its rolls and batches before the cut disappears.
        for (CutRoll line : cutRolls.findByCut(id)) {
            cutRollService.remove(line.getId());
        }
        cuts.delete(cut);
    }

    // --- model allocations -------------------------------------------------

    /**
     * Allocates pieces from this cut to a model at a branch. A repeat allocation
     * for the same cut + model + branch adjusts the existing row rather than
     * creating a duplicate.
     */
    public CutModelAllocationDto allocateToModel(Long cutId, CutModelAllocationRequest request) {
        Cut cut = require(cutId);
        cut.requireOpen();

        Model model = models.findById(request.modelId())
                .orElseThrow(() -> NotFoundException.of("Model", request.modelId()));
        Branch branch = requireBranch(request.branchId());

        var existing = modelAllocations.findByCutIdAndModelIdAndBranchId(cutId, model.getId(), branch.getId());
        CutModelAllocation allocation = existing.orElseGet(() -> {
            CutModelAllocation created = new CutModelAllocation();
            created.setCut(cut);
            created.setModel(model);
            created.setBranch(branch);
            return created;
        });

        if (!cutModelSizes.findByCutIdAndModelId(cutId, model.getId()).isEmpty()) {
            throw new BusinessRuleException("allocation_is_derived",
                    ("Model %s has a size breakdown on this cut, so its branch split comes from "
                                    + "which sizes each branch sews. Change the sizes instead.")
                            .formatted(model.getModelNumber()));
        }

        int previousQuantity = existing.map(CutModelAllocation::getQuantityAllocated).orElse(0);
        allocation.setQuantityAllocated(request.quantityAllocated());
        allocation.setNote(request.note());
        CutModelAllocation saved = existing.isPresent() ? allocation : modelAllocations.save(allocation);

        // Allocated pieces are the model's plan, so the pipeline moves with them:
        // the difference enters (or leaves) the CUTTING stage for this branch.
        pipeline.applyAllocationDelta(
                model, branch, request.quantityAllocated() - previousQuantity, cut.getCutDate());

        audit.record(
                existing.isPresent() ? AuditService.MODEL_ALLOCATION_CHANGED : AuditService.MODEL_ALLOCATED,
                "CutModelAllocation", saved.getId(), branch,
                BigDecimal.valueOf(saved.getQuantityAllocated()),
                "Cut %s to model %s".formatted(cut.getCutNumber(), model.getModelNumber()));

        return CutModelAllocationDto.from(saved);
    }

    public void removeModelAllocation(Long allocationId) {
        CutModelAllocation allocation = modelAllocations.findById(allocationId)
                .orElseThrow(() -> NotFoundException.of("Allocation", allocationId));
        allocation.getCut().requireOpen();

        pipeline.applyAllocationDelta(
                allocation.getModel(),
                allocation.getBranch(),
                -allocation.getQuantityAllocated(),
                allocation.getCut().getCutDate());

        audit.record(AuditService.MODEL_ALLOCATION_REMOVED, "CutModelAllocation", allocationId,
                allocation.getBranch(), BigDecimal.valueOf(allocation.getQuantityAllocated()), null);
        modelAllocations.delete(allocation);
    }

    // --- rolls ---------------------------------------------------------------

    public CutRollDto addRoll(Long cutId, CutRollRequest request) {
        Cut cut = require(cutId);
        CutRollDto added = cutRollService.addOrUpdate(cut, request);
        // Layers just changed, and the marker multiplies by them.
        recomputeAllAllocations(cut);
        return added;
    }

    public void removeRoll(Long cutRollId) {
        Cut cut = cutRolls.findById(cutRollId)
                .orElseThrow(() -> NotFoundException.of("Cut roll", cutRollId))
                .getCut();
        cutRollService.remove(cutRollId);
        recomputeAllAllocations(cut);
    }

    // --- the marker ----------------------------------------------------------

    /**
     * Sets how many pieces of one size a layer yields for one model on this cut.
     *
     * <p>This is the input the piece counts derive from — allocation quantities
     * follow it rather than being typed independently.
     */
    public CutDto setModelSize(Long cutId, CutModelSizeRequest request) {
        Cut cut = require(cutId);
        cut.requireOpen();

        Model model = resolveModel(cut, request);
        GarmentSize size = sizes.findById(request.garmentSizeId())
                .orElseThrow(() -> NotFoundException.of("Size", request.garmentSizeId()));

        // Populate before saving: the row carries a positive-count check, so an
        // insert with the field still at its default would be rejected.
        CutModelSize row = cutModelSizes
                .findByCutIdAndModelIdAndSizeId(cutId, model.getId(), size.getId())
                .orElseGet(() -> {
                    CutModelSize created = new CutModelSize();
                    created.setCut(cut);
                    created.setModel(model);
                    created.setSize(size);
                    return created;
                });
        row.setPiecesPerLayer(request.piecesPerLayer());
        row.setBranch(request.branchId() == null ? null : requireBranch(request.branchId()));
        if (row.isNew()) {
            cutModelSizes.save(row);
        }

        recomputeAllocations(cut, model);
        return get(cutId);
    }

    public CutDto removeModelSize(Long cutId, Long modelId, Long sizeId) {
        Cut cut = require(cutId);
        cut.requireOpen();
        cutModelSizes.findByCutIdAndModelIdAndSizeId(cutId, modelId, sizeId)
                .ifPresent(cutModelSizes::delete);

        models.findById(modelId).ifPresent(model -> recomputeAllocations(cut, model));
        return get(cutId);
    }

    /**
     * Finds the model by id, or by number — creating it when the number is new.
     *
     * <p>A model first shows up on the cutting table, so making the user leave and
     * register it before they can record the cut would be backwards. The name
     * defaults to the number when none is given.
     */
    private Model resolveModel(Cut cut, CutModelSizeRequest request) {
        String typed = request.modelNumber() == null ? "" : request.modelNumber().trim();
        if (request.modelId() == null && typed.isEmpty() && cut.getPrimaryModel() != null) {
            // The cut already names its model; the size step need not repeat it.
            return cut.getPrimaryModel();
        }
        if (request.modelId() != null) {
            return models.findById(request.modelId())
                    .orElseThrow(() -> NotFoundException.of("Model", request.modelId()));
        }

        String number = request.modelNumber() == null ? "" : request.modelNumber().trim();
        if (number.isEmpty()) {
            throw new BusinessRuleException("model_required",
                    "Name the model this size belongs to");
        }

        return models.findByModelNumberIgnoreCase(number).orElseGet(() -> {
            Model created = new Model();
            created.setModelNumber(number);
            String name = request.modelNameAr() == null ? "" : request.modelNameAr().trim();
            created.setNameAr(name.isEmpty() ? number : name);
            created.setActive(true);
            return models.save(created);
        });
    }

    /**
     * Rebuilds a model's branch allocations from the marker.
     *
     * <p>Pieces for a branch are the cut's layers multiplied by the pieces-per-layer
     * of whichever sizes that branch sews. Because it is derived, the split can
     * never disagree with the marker — and adding a roll, which changes the layer
     * count, flows straight through to the planned quantities.
     *
     * <p>Each change is pushed to the pipeline as a delta, so a reduction that
     * would undo recorded progress is refused rather than silently applied.
     */
    private void recomputeAllocations(Cut cut, Model model) {
        List<CutModelSize> markerRows = cutModelSizes.findByCutIdAndModelId(cut.getId(), model.getId());
        if (markerRows.isEmpty()) {
            return;
        }

        int layers = totalLayers(cut.getId());

        // Sizes inherit the model's sewing branch; failing that, the cut's own.
        Branch fallback = model.getSewingBranch() != null ? model.getSewingBranch() : cut.getBranch();
        Map<Long, Branch> branchesById = new LinkedHashMap<>();
        Map<Long, Integer> piecesByBranch = new LinkedHashMap<>();

        for (CutModelSize row : markerRows) {
            Branch branch = row.getBranch() != null ? row.getBranch() : fallback;
            branchesById.putIfAbsent(branch.getId(), branch);
            piecesByBranch.merge(branch.getId(), layers * row.getPiecesPerLayer(), Integer::sum);
        }

        // Branches that no longer sew any of this model's sizes drop to zero.
        for (CutModelAllocation existing : modelAllocations.findByCut(cut.getId())) {
            if (!existing.getModel().getId().equals(model.getId())) {
                continue;
            }
            Long branchId = existing.getBranch().getId();
            if (!piecesByBranch.containsKey(branchId)) {
                pipeline.applyAllocationDelta(model, existing.getBranch(),
                        -existing.getQuantityAllocated(), cut.getCutDate());
                modelAllocations.delete(existing);
            }
        }

        for (var entry : piecesByBranch.entrySet()) {
            Branch branch = branchesById.get(entry.getKey());
            int quantity = entry.getValue();

            var existing = modelAllocations.findByCutIdAndModelIdAndBranchId(
                    cut.getId(), model.getId(), branch.getId());
            int previous = existing.map(CutModelAllocation::getQuantityAllocated).orElse(0);
            if (previous == quantity) {
                continue;
            }

            CutModelAllocation allocation = existing.orElseGet(() -> {
                CutModelAllocation created = new CutModelAllocation();
                created.setCut(cut);
                created.setModel(model);
                created.setBranch(branch);
                created.setQuantityAllocated(quantity);
                return modelAllocations.save(created);
            });
            allocation.setQuantityAllocated(quantity);

            pipeline.applyAllocationDelta(model, branch, quantity - previous, cut.getCutDate());
            audit.record(AuditService.MODEL_ALLOCATION_CHANGED, "CutModelAllocation", allocation.getId(),
                    branch, BigDecimal.valueOf(quantity),
                    "Derived from the marker on cut %s".formatted(cut.getCutNumber()));
        }
    }

    /** Re-derives every model on the cut, e.g. after the layer count changes. */
    private void recomputeAllAllocations(Cut cut) {
        cutModelSizes.findByCut(cut.getId()).stream()
                .map(CutModelSize::getModel)
                .distinct()
                .forEach(model -> recomputeAllocations(cut, model));
    }

    // --- helpers -----------------------------------------------------------

    private void applyEditableFields(Cut cut, CutRequest request) {
        cut.setCutDate(request.cutDate());
        cut.setCutLength(request.cutLength());
        cut.setModelDescription(request.modelDescription());
        cut.setLabelAr(request.labelAr());
        cut.setLabelEn(request.labelEn());
        cut.setNote(request.note());
    }

    private Cut resolveParent(CutRequest request) {
        if (!request.cutType().requiresParent()) {
            if (request.parentMainCutId() != null) {
                throw new BusinessRuleException("cut_main_has_no_parent",
                        "A MAIN cut does not reference another cut");
            }
            return null;
        }
        if (request.parentMainCutId() == null) {
            throw new BusinessRuleException("cut_parent_required",
                    "A %s cut must reference the MAIN cut it belongs to".formatted(request.cutType()));
        }
        return cuts.findById(request.parentMainCutId())
                .orElseThrow(() -> NotFoundException.of("Main cut", request.parentMainCutId()));
    }

    /** Layers, weight and waste for a page of cuts, in one grouped query. */
    private Map<Long, Object[]> rollTotalsByCut(List<Long> cutIds) {
        if (cutIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Object[]> totals = new HashMap<>();
        for (Object[] row : cutRolls.totalsByCutIds(cutIds)) {
            totals.put((Long) row[0], new Object[] {row[1], row[2], row[3]});
        }
        return totals;
    }

    /** One grouped query for the whole page rather than one per cut. */
    private Map<Long, Long> totalsByCut(List<Long> cutIds) {
        if (cutIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> totals = new HashMap<>();
        for (Object[] row : modelAllocations.totalsByCutIds(cutIds)) {
            totals.put((Long) row[0], (Long) row[1]);
        }
        return totals;
    }

    private List<CutModelAllocationDto> modelAllocationDtos(Long cutId) {
        return modelAllocations.findByCut(cutId).stream().map(CutModelAllocationDto::from).toList();
    }

    /** Assembles the full cut view: rolls, marker, derived totals, allocations. */
    private CutDto detailOf(Cut cut) {
        List<CutRoll> rollLines = cutRolls.findByCut(cut.getId());
        int layers = rollLines.stream().mapToInt(CutRoll::getLayers).sum();
        BigDecimal consumed = rollLines.stream()
                .map(CutRoll::getWeightConsumed)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal defect = rollLines.stream()
                .map(CutRoll::getDefectWeight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Resolve inheritance here so the UI is told where each size is actually
        // sewn, not merely that the row left it unset.
        List<CutModelSizeDto> sizeRows = cutModelSizes.findByCut(cut.getId()).stream()
                .map(row -> {
                    Branch effective = row.getBranch() != null
                            ? row.getBranch()
                            : (row.getModel().getSewingBranch() != null
                                    ? row.getModel().getSewingBranch()
                                    : cut.getBranch());
                    return CutModelSizeDto.from(row, layers, effective);
                })
                .toList();

        return CutDto.detail(
                cut, layers, consumed, defect,
                derivedTotals(cut),
                modelAllocationDtos(cut.getId()),
                sizeRows,
                rollLines.stream().map(CutRollDto::from).toList());
    }

    private List<CutRollDto> rollDtos(Long cutId) {
        return cutRolls.findByCut(cutId).stream().map(CutRollDto::from).toList();
    }

    /** Layers summed across the cut's rolls — the multiplier the marker uses. */
    private int totalLayers(Long cutId) {
        return cutRolls.findByCut(cutId).stream().mapToInt(CutRoll::getLayers).sum();
    }

    /**
     * What the marker says each model yields, against what is allocated to
     * branches. A model with no marker rows reports zero derived pieces, which
     * leaves its allocation free-form — cuts entered the older way still work.
     */
    private List<CutModelDerivedDto> derivedTotals(Cut cut) {
        int layers = totalLayers(cut.getId());

        Map<Long, Integer> perLayer = new LinkedHashMap<>();
        Map<Long, Model> modelsById = new LinkedHashMap<>();
        for (CutModelSize row : cutModelSizes.findByCut(cut.getId())) {
            perLayer.merge(row.getModel().getId(), row.getPiecesPerLayer(), Integer::sum);
            modelsById.putIfAbsent(row.getModel().getId(), row.getModel());
        }

        Map<Long, Long> allocated = new LinkedHashMap<>();
        for (CutModelAllocation allocation : modelAllocations.findByCut(cut.getId())) {
            allocated.merge(allocation.getModel().getId(), (long) allocation.getQuantityAllocated(), Long::sum);
            modelsById.putIfAbsent(allocation.getModel().getId(), allocation.getModel());
        }

        return modelsById.values().stream()
                .map(model -> {
                    int piecesPerLayer = perLayer.getOrDefault(model.getId(), 0);
                    long derived = (long) layers * piecesPerLayer;
                    long allocatedPieces = allocated.getOrDefault(model.getId(), 0L);
                    return new CutModelDerivedDto(
                            model.getId(), model.getModelNumber(), model.getNameAr(),
                            piecesPerLayer, derived, allocatedPieces,
                            Math.max(0, derived - allocatedPieces),
                            derived == 0 || derived == allocatedPieces);
                })
                .toList();
    }

    /**
     * Branch allocations may not add up to more than the marker says the cut
     * yields. Without a marker there is nothing to check against, so the older
     * free-form entry still works.
     */
    private void requireWithinDerivedTotal(Cut cut, Model model, int previousQuantity, int newQuantity) {
        int piecesPerLayer = cutModelSizes.findByCutIdAndModelId(cut.getId(), model.getId()).stream()
                .mapToInt(CutModelSize::getPiecesPerLayer)
                .sum();
        if (piecesPerLayer == 0) {
            return;
        }

        long derived = (long) totalLayers(cut.getId()) * piecesPerLayer;
        long otherBranches = modelAllocations.findByCut(cut.getId()).stream()
                .filter(a -> a.getModel().getId().equals(model.getId()))
                .mapToLong(CutModelAllocation::getQuantityAllocated)
                .sum() - previousQuantity;

        if (otherBranches + newQuantity > derived) {
            throw new BusinessRuleException("allocation_exceeds_derived_pieces",
                    ("Model %s yields %d pieces from this cut (%d layers x %d per layer); "
                                    + "allocating %d would take the total to %d.")
                            .formatted(model.getModelNumber(), derived, totalLayers(cut.getId()), piecesPerLayer,
                                    newQuantity, otherBranches + newQuantity));
        }
    }

    private Cut require(Long id) {
        return cuts.findById(id).orElseThrow(() -> NotFoundException.of("Cut", id));
    }

    /**
     * Finds or creates the model a cut is opened for.
     *
     * <p>Nearly every cut introduces a new model, so a number that does not exist
     * yet is created here rather than sending the user off to register it first.
     * The name falls back to the number, and the sewing branch to the cut's own.
     */
    private Model resolvePrimaryModel(CutRequest request) {
        String number = request.modelNumber() == null ? "" : request.modelNumber().trim();
        if (number.isEmpty()) {
            return null;
        }

        return models.findByModelNumberIgnoreCase(number)
                .map(existing -> {
                    // Fill in a sewing branch that was left unset when it is offered now.
                    if (existing.getSewingBranch() == null && request.modelSewingBranchId() != null) {
                        existing.setSewingBranch(requireBranch(request.modelSewingBranchId()));
                    }
                    return existing;
                })
                .orElseGet(() -> {
                    Model created = new Model();
                    created.setModelNumber(number);
                    String name = request.modelNameAr() == null ? "" : request.modelNameAr().trim();
                    created.setNameAr(name.isEmpty() ? number : name);
                    created.setSewingBranch(request.modelSewingBranchId() == null
                            ? requireBranch(request.branchId())
                            : requireBranch(request.modelSewingBranchId()));
                    created.setActive(true);
                    return models.save(created);
                });
    }

    private FabricType resolveFabricType(Long id) {
        return id == null
                ? null
                : fabricTypes.findById(id).orElseThrow(() -> NotFoundException.of("Fabric type", id));
    }

    private Branch requireBranch(Long id) {
        return branches.findById(id).orElseThrow(() -> NotFoundException.of("Branch", id));
    }
}
