package com.apparel.tracking.reference.web;

import java.util.List;

import com.apparel.tracking.reference.dto.BranchDto;
import com.apparel.tracking.reference.dto.PipelineStageDto;
import com.apparel.tracking.reference.repository.BranchRepository;
import com.apparel.tracking.reference.repository.PipelineStageRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only lookup data the UI needs to render branch and stage selectors. */
@RestController
@RequestMapping("/api/reference")
@Tag(name = "Reference data")
public class ReferenceDataController {

    private final BranchRepository branches;
    private final PipelineStageRepository stages;

    public ReferenceDataController(BranchRepository branches, PipelineStageRepository stages) {
        this.branches = branches;
        this.stages = stages;
    }

    @GetMapping("/branches")
    @Operation(summary = "List active branches, in display order")
    public List<BranchDto> branches() {
        return branches.findAllByActiveTrueOrderBySortOrderAsc().stream().map(BranchDto::from).toList();
    }

    @GetMapping("/stages")
    @Operation(summary = "List active pipeline stages, in pipeline order")
    public List<PipelineStageDto> stages() {
        return stages.findAllByActiveTrueOrderBySequenceNoAsc().stream().map(PipelineStageDto::from).toList();
    }
}
