package com.apparel.tracking.pipeline.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.apparel.tracking.common.exception.BusinessRuleException;
import com.apparel.tracking.common.exception.NotFoundException;
import com.apparel.tracking.config.JpaAuditingConfig;
import com.apparel.tracking.pipeline.domain.FlagAction;
import com.apparel.tracking.pipeline.domain.ModelBranchStageCount;
import com.apparel.tracking.pipeline.domain.MovementReason;
import com.apparel.tracking.pipeline.domain.PieceFlagEvent;
import com.apparel.tracking.pipeline.domain.StageMovement;
import com.apparel.tracking.pipeline.dto.BranchPipelineDto;
import com.apparel.tracking.pipeline.dto.FlagRequest;
import com.apparel.tracking.pipeline.dto.ModelPipelineDto;
import com.apparel.tracking.pipeline.dto.ReceiveRequest;
import com.apparel.tracking.pipeline.dto.SellRequest;
import com.apparel.tracking.pipeline.dto.StageCountDto;
import com.apparel.tracking.pipeline.dto.StageMoveRequest;
import com.apparel.tracking.pipeline.repository.ModelBranchStageCountRepository;
import com.apparel.tracking.pipeline.repository.PieceFlagEventRepository;
import com.apparel.tracking.pipeline.repository.StageMovementRepository;
import com.apparel.tracking.production.domain.Model;
import com.apparel.tracking.production.repository.CutModelAllocationRepository;
import com.apparel.tracking.production.repository.ModelRepository;
import com.apparel.tracking.reference.domain.Branch;
import com.apparel.tracking.reference.domain.PipelineStage;
import com.apparel.tracking.reference.repository.BranchRepository;
import com.apparel.tracking.reference.repository.PipelineStageRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Piece counts per model, per branch, per stage — and the only code allowed to
 * change them.
 *
 * <p>The invariant, for every model and branch:
 * <pre>sum(stage piece counts) == sum(allocated pieces from every cut)</pre>
 *
 * <p>It holds by construction. Pieces enter only when a cut allocates them,
 * leave only when an allocation is reduced, and every other operation moves
 * pieces between stages without changing the total. Nothing edits a count
 * directly.
 */
@Service
@Transactional
public class PipelineService {

    public static final String STAGE_CUTTING = "CUTTING";
    public static final String STAGE_RECEIVED = "RECEIVED";
    public static final String STAGE_SOLD = "SOLD";

    private final ModelBranchStageCountRepository counts;
    private final StageMovementRepository movements;
    private final PieceFlagEventRepository flagEvents;
    private final ModelRepository models;
    private final BranchRepository branches;
    private final PipelineStageRepository stages;
    private final CutModelAllocationRepository allocations;

    public PipelineService(
            ModelBranchStageCountRepository counts,
            StageMovementRepository movements,
            PieceFlagEventRepository flagEvents,
            ModelRepository models,
            BranchRepository branches,
            PipelineStageRepository stages,
            CutModelAllocationRepository allocations) {
        this.counts = counts;
        this.movements = movements;
        this.flagEvents = flagEvents;
        this.models = models;
        this.branches = branches;
        this.stages = stages;
        this.allocations = allocations;
    }

    // --- called by the cuts module when allocations change -------------------

    /**
     * Keeps the pipeline in step with an allocation change.
     *
     * <p>An increase seeds pieces into CUTTING. A decrease takes them out of
     * CUTTING only: if CUTTING no longer holds enough, the change is rejected
     * rather than silently unwinding progress somebody recorded further down the
     * line.
     */
    public void applyAllocationDelta(Model model, Branch branch, int delta, LocalDate cutDate) {
        if (delta == 0) {
            return;
        }
        PipelineStage cutting = requireStage(STAGE_CUTTING);
        ModelBranchStageCount cuttingCount = countFor(model, branch, cutting);

        if (delta > 0) {
            // Dated to the cut: these pieces physically entered cutting that day.
            cuttingCount.add(delta);
            logMovement(model, branch, null, cutting, delta, cutDate, MovementReason.ALLOCATION_ADDED, null);
            return;
        }

        int reduction = -delta;
        if (cuttingCount.getPieceCount() < reduction) {
            throw new BusinessRuleException("allocation_below_pipeline_progress",
                    ("Cutting holds %d pieces of model %s at %s, so the allocation cannot drop by %d. "
                                    + "Move pieces back to cutting first.")
                            .formatted(cuttingCount.getPieceCount(), model.getModelNumber(), branch.getCode(),
                                    reduction));
        }
        // Dated to today: a reduction is a correction being made now, not something
        // that happened back on the cutting date.
        cuttingCount.remove(reduction);
        logMovement(model, branch, cutting, null, reduction, LocalDate.now(), MovementReason.ALLOCATION_REDUCED, null);
    }

    // --- actions -------------------------------------------------------------

    /**
     * The receiving action: N pieces arrived from a branch.
     *
     * <p>They are drawn from whichever earlier stages hold them, closest to
     * RECEIVED first (so SEWING before CUTTING). There is deliberately no rule
     * about which stage they must come from.
     */
    public ModelPipelineDto receive(ReceiveRequest request) {
        Model model = requireModel(request.modelId());
        Branch branch = requireBranch(request.branchId());
        PipelineStage received = requireStage(STAGE_RECEIVED);

        drawFromEarlierStages(model, branch, received, request.quantity(), request.receivedDate(),
                MovementReason.RECEIVING, request.note());

        countFor(model, branch, received).add(request.quantity());
        return pipelineForModel(model.getId());
    }

    /** Moves received pieces to SOLD. Flagged pieces are non-sellable and stay behind. */
    public ModelPipelineDto sell(SellRequest request) {
        Model model = requireModel(request.modelId());
        Branch branch = requireBranch(request.branchId());
        PipelineStage received = requireStage(STAGE_RECEIVED);
        PipelineStage sold = requireStage(STAGE_SOLD);

        ModelBranchStageCount receivedCount = countFor(model, branch, received);
        if (request.quantity() > receivedCount.sellableCount()) {
            throw new BusinessRuleException("not_enough_sellable_pieces",
                    ("Received holds %d pieces of which %d are flagged as non-sellable, "
                                    + "so only %d can be sold — %d requested.")
                            .formatted(receivedCount.getPieceCount(), receivedCount.getFlaggedCount(),
                                    receivedCount.sellableCount(), request.quantity()));
        }

        receivedCount.remove(request.quantity());
        countFor(model, branch, sold).add(request.quantity());
        logMovement(model, branch, received, sold, request.quantity(), request.soldDate(),
                MovementReason.SALE, request.note());

        return pipelineForModel(model.getId());
    }

    /** A plain move between two named stages, for corrections and other progress. */
    public ModelPipelineDto move(StageMoveRequest request) {
        Model model = requireModel(request.modelId());
        Branch branch = requireBranch(request.branchId());
        PipelineStage from = requireStage(request.fromStageCode());
        PipelineStage to = requireStage(request.toStageCode());

        if (from.getId().equals(to.getId())) {
            throw new BusinessRuleException("stage_move_same_stage",
                    "The source and destination stages are the same");
        }

        countFor(model, branch, from).remove(request.quantity());
        countFor(model, branch, to).add(request.quantity());
        logMovement(model, branch, from, to, request.quantity(), request.movementDate(),
                MovementReason.STAGE_MOVE, request.note());

        return pipelineForModel(model.getId());
    }

    /**
     * Marks pieces defective. Only permitted at RECEIVED or later: defects are
     * found at receiving inspection, so an earlier stage has nothing to flag.
     */
    public ModelPipelineDto flag(FlagRequest request) {
        return applyFlag(request, FlagAction.FLAG);
    }

    /** Clears a defect mark, for example after a piece was repaired. */
    public ModelPipelineDto unflag(FlagRequest request) {
        return applyFlag(request, FlagAction.UNFLAG);
    }

    private ModelPipelineDto applyFlag(FlagRequest request, FlagAction action) {
        Model model = requireModel(request.modelId());
        Branch branch = requireBranch(request.branchId());
        PipelineStage stage = requireStage(request.stageCode() == null ? STAGE_RECEIVED : request.stageCode());
        PipelineStage received = requireStage(STAGE_RECEIVED);

        if (stage.getSequenceNo() < received.getSequenceNo()) {
            throw new BusinessRuleException("flag_stage_too_early",
                    "Defects are recorded from the %s stage onwards, not at %s"
                            .formatted(received.getCode(), stage.getCode()));
        }

        ModelBranchStageCount count = countFor(model, branch, stage);
        if (action == FlagAction.FLAG) {
            count.flag(request.quantity());
        } else {
            count.unflag(request.quantity());
        }

        PieceFlagEvent event = new PieceFlagEvent();
        event.setModel(model);
        event.setBranch(branch);
        event.setStage(stage);
        event.setAction(action);
        event.setQuantity(request.quantity());
        event.setReason(request.reason());
        event.setEventDate(request.eventDate());
        event.setCreatedBy(JpaAuditingConfig.currentUsername());
        flagEvents.save(event);

        return pipelineForModel(model.getId());
    }

    // --- reads ---------------------------------------------------------------

    @Transactional(readOnly = true)
    public ModelPipelineDto pipelineForModel(Long modelId) {
        Model model = requireModel(modelId);
        Map<Long, Long> planned = plannedByBranch(modelId);
        Map<Long, List<ModelBranchStageCount>> byBranch = counts.search(modelId, null).stream()
                .collect(Collectors.groupingBy(count -> count.getBranch().getId()));

        // Include branches that have a plan but no counts yet, so a model that was
        // just allocated still shows a row.
        List<Long> branchIds = new ArrayList<>(planned.keySet());
        byBranch.keySet().forEach(id -> {
            if (!branchIds.contains(id)) {
                branchIds.add(id);
            }
        });

        List<BranchPipelineDto> branchRows = branchIds.stream()
                .map(branchId -> branchRow(branchId, planned.getOrDefault(branchId, 0L),
                        byBranch.getOrDefault(branchId, List.of())))
                .sorted(Comparator.comparing(BranchPipelineDto::branchCode))
                .toList();

        long plannedTotal = branchRows.stream().mapToLong(BranchPipelineDto::plannedQuantity).sum();
        long inPipeline = branchRows.stream().mapToLong(BranchPipelineDto::totalInPipeline).sum();
        long flagged = branchRows.stream().mapToLong(BranchPipelineDto::flaggedTotal).sum();

        return new ModelPipelineDto(
                model.getId(),
                model.getModelNumber(),
                model.getNameAr(),
                model.getNameEn(),
                plannedTotal,
                inPipeline,
                flagged,
                plannedTotal == inPipeline,
                branchRows);
    }

    @Transactional(readOnly = true)
    public List<ModelPipelineDto> pipelineForAllModels() {
        return models.findAllByOrderByModelNumberAsc().stream()
                .map(model -> pipelineForModel(model.getId()))
                .toList();
    }

    private BranchPipelineDto branchRow(Long branchId, long planned, List<ModelBranchStageCount> rows) {
        Branch branch = requireBranch(branchId);
        List<StageCountDto> stageRows = allStagesFor(branch, rows);

        long total = stageRows.stream().mapToLong(StageCountDto::pieceCount).sum();
        long flagged = stageRows.stream().mapToLong(StageCountDto::flaggedCount).sum();

        return new BranchPipelineDto(
                branch.getId(), branch.getCode(), branch.getNameAr(), branch.getNameEn(),
                planned, total, flagged, planned == total, stageRows);
    }

    /** Every active stage appears, zero-filled, so the UI renders a complete row. */
    private List<StageCountDto> allStagesFor(Branch branch, List<ModelBranchStageCount> rows) {
        Map<Long, ModelBranchStageCount> byStage = rows.stream()
                .collect(Collectors.toMap(row -> row.getStage().getId(), row -> row));

        return stages.findAllByActiveTrueOrderBySequenceNoAsc().stream()
                .map(stage -> {
                    ModelBranchStageCount row = byStage.get(stage.getId());
                    return row != null
                            ? StageCountDto.from(row)
                            : new StageCountDto(stage.getId(), stage.getCode(), stage.getNameAr(), stage.getNameEn(),
                                    stage.getSequenceNo(), 0, 0);
                })
                .toList();
    }

    private Map<Long, Long> plannedByBranch(Long modelId) {
        return allocations.plannedByModelAndBranch(modelId).stream()
                .collect(Collectors.toMap(row -> row.branchId(), row -> row.plannedQuantity(), Long::sum));
    }

    // --- helpers -------------------------------------------------------------

    /**
     * Takes {@code quantity} pieces out of the stages before {@code target},
     * starting with the one closest to it.
     */
    private void drawFromEarlierStages(
            Model model,
            Branch branch,
            PipelineStage target,
            int quantity,
            LocalDate date,
            MovementReason reason,
            String note) {
        List<ModelBranchStageCount> earlier = counts.findByModelAndBranch(model.getId(), branch.getId()).stream()
                .filter(row -> row.getStage().getSequenceNo() < target.getSequenceNo())
                .filter(row -> row.getPieceCount() > 0)
                .sorted(Comparator.comparingInt((ModelBranchStageCount row) -> row.getStage().getSequenceNo())
                        .reversed())
                .toList();

        int available = earlier.stream().mapToInt(ModelBranchStageCount::getPieceCount).sum();
        if (available < quantity) {
            throw new BusinessRuleException("not_enough_pieces_in_progress",
                    "Only %d pieces of model %s are in progress at %s, cannot move %d to %s"
                            .formatted(available, model.getModelNumber(), branch.getCode(), quantity,
                                    target.getCode()));
        }

        int remaining = quantity;
        for (ModelBranchStageCount row : earlier) {
            if (remaining == 0) {
                break;
            }
            int taken = Math.min(remaining, row.getPieceCount());
            row.remove(taken);
            logMovement(model, branch, row.getStage(), target, taken, date, reason, note);
            remaining -= taken;
        }
    }

    /** Fetches the count row, creating a zeroed one the first time it is needed. */
    private ModelBranchStageCount countFor(Model model, Branch branch, PipelineStage stage) {
        return counts.findByModelIdAndBranchIdAndStageId(model.getId(), branch.getId(), stage.getId())
                .orElseGet(() -> {
                    ModelBranchStageCount created = new ModelBranchStageCount();
                    created.setModel(model);
                    created.setBranch(branch);
                    created.setStage(stage);
                    return counts.save(created);
                });
    }

    private void logMovement(
            Model model,
            Branch branch,
            PipelineStage from,
            PipelineStage to,
            int quantity,
            LocalDate date,
            MovementReason reason,
            String note) {
        StageMovement movement = new StageMovement();
        movement.setModel(model);
        movement.setBranch(branch);
        movement.setFromStage(from);
        movement.setToStage(to);
        movement.setQuantity(quantity);
        movement.setMovementDate(date);
        movement.setReason(reason);
        movement.setNote(note);
        movement.setCreatedBy(JpaAuditingConfig.currentUsername());
        movements.save(movement);
    }

    private Model requireModel(Long id) {
        return models.findById(id).orElseThrow(() -> NotFoundException.of("Model", id));
    }

    private Branch requireBranch(Long id) {
        return branches.findById(id).orElseThrow(() -> NotFoundException.of("Branch", id));
    }

    private PipelineStage requireStage(String code) {
        return stages.findByCode(code)
                .orElseThrow(() -> new NotFoundException("Pipeline stage '%s' was not found".formatted(code)));
    }
}
