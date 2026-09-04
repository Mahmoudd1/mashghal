package com.apparel.tracking.report.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.apparel.tracking.common.exception.NotFoundException;
import com.apparel.tracking.fabric.dto.FabricStockDto;
import com.apparel.tracking.fabric.dto.IntakeRemainingRowDto;
import com.apparel.tracking.fabric.dto.OpenRollRowDto;
import com.apparel.tracking.fabric.service.FabricIntakeService;
import com.apparel.tracking.pipeline.repository.ModelBranchStageCountRepository;
import com.apparel.tracking.production.repository.CutModelAllocationRepository;
import com.apparel.tracking.reference.domain.Branch;
import com.apparel.tracking.reference.domain.PipelineStage;
import com.apparel.tracking.reference.repository.BranchRepository;
import com.apparel.tracking.reference.repository.PipelineStageRepository;
import com.apparel.tracking.report.dto.BranchRollupDto;
import com.apparel.tracking.report.dto.FlaggedRowDto;
import com.apparel.tracking.report.dto.OverviewDto;
import com.apparel.tracking.report.dto.StageTotalDto;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only rollups over the pipeline and fabric data.
 *
 * <p>Every figure is aggregated in the database rather than by walking models in
 * Java, so a branch report costs the same whether there are ten models or ten
 * thousand.
 */
@Service
@Transactional(readOnly = true)
public class ReportService {

    private final ModelBranchStageCountRepository counts;
    private final CutModelAllocationRepository allocations;
    private final BranchRepository branches;
    private final PipelineStageRepository stages;
    private final FabricIntakeService fabric;

    public ReportService(
            ModelBranchStageCountRepository counts,
            CutModelAllocationRepository allocations,
            BranchRepository branches,
            PipelineStageRepository stages,
            FabricIntakeService fabric) {
        this.counts = counts;
        this.allocations = allocations;
        this.branches = branches;
        this.stages = stages;
        this.fabric = fabric;
    }

    /** Pipeline totals for one branch, across every model made there. */
    public BranchRollupDto branchRollup(Long branchId) {
        Branch branch = branches.findById(branchId)
                .orElseThrow(() -> NotFoundException.of("Branch", branchId));

        List<StageTotalDto> stageTotals = zeroFill(counts.stageTotals(branchId));
        long planned = plannedByBranch().getOrDefault(branchId, 0L);
        long inPipeline = sumPieces(stageTotals);

        return new BranchRollupDto(
                branch.getId(), branch.getCode(), branch.getNameAr(), branch.getNameEn(),
                planned, inPipeline, sumFlagged(stageTotals), planned == inPipeline,
                counts.countModelsWithPieces(branchId), stageTotals);
    }

    /** Totals across every model and branch, with a per-branch breakdown. */
    public OverviewDto overview() {
        List<StageTotalDto> stageTotals = zeroFill(counts.stageTotals(null));
        Map<Long, Long> planned = plannedByBranch();
        Map<Long, Map<Long, long[]>> byBranch = stageTotalsByBranch();

        List<BranchRollupDto> branchRows = new ArrayList<>();
        for (Branch branch : branches.findAllByActiveTrueOrderBySortOrderAsc()) {
            List<StageTotalDto> rows = stageRowsFor(byBranch.getOrDefault(branch.getId(), Map.of()));
            long branchPlanned = planned.getOrDefault(branch.getId(), 0L);
            long branchPieces = sumPieces(rows);

            branchRows.add(new BranchRollupDto(
                    branch.getId(), branch.getCode(), branch.getNameAr(), branch.getNameEn(),
                    branchPlanned, branchPieces, sumFlagged(rows), branchPlanned == branchPieces,
                    counts.countModelsWithPieces(branch.getId()), rows));
        }

        long plannedTotal = planned.values().stream().mapToLong(Long::longValue).sum();
        long inPipeline = sumPieces(stageTotals);

        return new OverviewDto(
                plannedTotal, inPipeline, sumFlagged(stageTotals), plannedTotal == inPipeline,
                counts.countModelsWithPieces(null), stageTotals, branchRows);
    }

    /** Flagged pieces per model, branch and stage. Both filters are optional. */
    public List<FlaggedRowDto> flagged(Long modelId, Long branchId) {
        return counts.flaggedRows(modelId, branchId);
    }

    /** Fabric stock per type and pool — the same rollup the fabric module serves. */
    public List<FabricStockDto> fabricStock(Long fabricTypeId) {
        return fabric.stock(fabricTypeId);
    }

    /** Rolls sitting part-used right now, per fabric type and colour. */
    public List<OpenRollRowDto> openRolls(Long fabricTypeId) {
        return fabric.openRolls(fabricTypeId);
    }

    /** "How many rolls are left from each date", across every fabric type. */
    public List<IntakeRemainingRowDto> fabricRemainingByDate(Long fabricTypeId, boolean inStockOnly) {
        return fabric.remainingByDate(fabricTypeId, inStockOnly);
    }

    // --- helpers -------------------------------------------------------------

    private Map<Long, Long> plannedByBranch() {
        Map<Long, Long> result = new HashMap<>();
        for (Object[] row : allocations.plannedTotalsByBranch()) {
            result.put((Long) row[0], (Long) row[1]);
        }
        return result;
    }

    /** branch id -> stage id -> [pieces, flagged] */
    private Map<Long, Map<Long, long[]>> stageTotalsByBranch() {
        Map<Long, Map<Long, long[]>> result = new HashMap<>();
        for (Object[] row : counts.stageTotalsByBranch()) {
            result.computeIfAbsent((Long) row[0], key -> new HashMap<>())
                    .put((Long) row[1], new long[] {(Long) row[2], (Long) row[3]});
        }
        return result;
    }

    /** Every active stage appears even with no pieces, so rows line up across branches. */
    private List<StageTotalDto> stageRowsFor(Map<Long, long[]> totals) {
        return stages.findAllByActiveTrueOrderBySequenceNoAsc().stream()
                .map(stage -> {
                    long[] value = totals.getOrDefault(stage.getId(), new long[] {0L, 0L});
                    return toDto(stage, value[0], value[1]);
                })
                .toList();
    }

    private List<StageTotalDto> zeroFill(List<StageTotalDto> rows) {
        Map<Long, StageTotalDto> byStage = new HashMap<>();
        rows.forEach(row -> byStage.put(row.stageId(), row));

        return stages.findAllByActiveTrueOrderBySequenceNoAsc().stream()
                .map(stage -> byStage.getOrDefault(stage.getId(), toDto(stage, 0L, 0L)))
                .toList();
    }

    private StageTotalDto toDto(PipelineStage stage, long pieces, long flagged) {
        return new StageTotalDto(
                stage.getId(), stage.getCode(), stage.getNameAr(), stage.getNameEn(),
                stage.getSequenceNo(), pieces, flagged);
    }

    private long sumPieces(List<StageTotalDto> rows) {
        return rows.stream().mapToLong(StageTotalDto::pieceCount).sum();
    }

    private long sumFlagged(List<StageTotalDto> rows) {
        return rows.stream().mapToLong(StageTotalDto::flaggedCount).sum();
    }
}
