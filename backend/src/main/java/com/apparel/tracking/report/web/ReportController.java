package com.apparel.tracking.report.web;

import java.util.List;

import com.apparel.tracking.fabric.dto.FabricStockDto;
import com.apparel.tracking.fabric.dto.IntakeRemainingRowDto;
import com.apparel.tracking.fabric.dto.OpenRollRowDto;
import com.apparel.tracking.pipeline.dto.ModelPipelineDto;
import com.apparel.tracking.pipeline.service.PipelineService;
import com.apparel.tracking.report.dto.BranchRollupDto;
import com.apparel.tracking.report.dto.FlaggedRowDto;
import com.apparel.tracking.report.dto.OverviewDto;
import com.apparel.tracking.report.service.ReportService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
@Tag(name = "Reports")
public class ReportController {

    private final ReportService reports;
    private final PipelineService pipeline;

    public ReportController(ReportService reports, PipelineService pipeline) {
        this.reports = reports;
        this.pipeline = pipeline;
    }

    @GetMapping("/overview")
    @Operation(summary = "Pipeline totals across every model and branch")
    public OverviewDto overview() {
        return reports.overview();
    }

    @GetMapping("/branches/{branchId}")
    @Operation(summary = "Pipeline totals for one branch across all its models")
    public BranchRollupDto branch(@PathVariable Long branchId) {
        return reports.branchRollup(branchId);
    }

    @GetMapping("/models/{modelId}")
    @Operation(summary = "One model's pipeline, per branch")
    public ModelPipelineDto model(@PathVariable Long modelId) {
        return pipeline.pipelineForModel(modelId);
    }

    @GetMapping("/flagged")
    @Operation(summary = "Flagged pieces per model, branch and stage")
    public List<FlaggedRowDto> flagged(
            @RequestParam(required = false) Long modelId,
            @RequestParam(required = false) Long branchId) {
        return reports.flagged(modelId, branchId);
    }

    @GetMapping("/fabric-inventory")
    @Operation(summary = "Stock per fabric type and pool, with the indicative colour breakdown")
    public List<FabricStockDto> fabricStock(@RequestParam(required = false) Long fabricTypeId) {
        return reports.fabricStock(fabricTypeId);
    }

    @GetMapping("/open-rolls")
    @Operation(summary = "Partially consumed rolls, per fabric type and colour")
    public List<OpenRollRowDto> openRolls(@RequestParam(required = false) Long fabricTypeId) {
        return reports.openRolls(fabricTypeId);
    }

    @GetMapping("/fabric-remaining-by-date")
    @Operation(summary = "How many rolls are left from each intake date")
    public List<IntakeRemainingRowDto> fabricRemainingByDate(
            @RequestParam(required = false) Long fabricTypeId,
            @RequestParam(defaultValue = "false") boolean inStockOnly) {
        return reports.fabricRemainingByDate(fabricTypeId, inStockOnly);
    }
}
