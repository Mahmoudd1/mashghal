package com.apparel.tracking.pipeline.web;

import java.util.List;

import com.apparel.tracking.pipeline.dto.FlagRequest;
import com.apparel.tracking.pipeline.dto.ModelPipelineDto;
import com.apparel.tracking.pipeline.dto.ReceiveRequest;
import com.apparel.tracking.pipeline.dto.SellRequest;
import com.apparel.tracking.pipeline.dto.StageMoveRequest;
import com.apparel.tracking.pipeline.service.PipelineService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pipeline")
@Tag(name = "Pipeline")
public class PipelineController {

    private final PipelineService service;

    public PipelineController(PipelineService service) {
        this.service = service;
    }

    @GetMapping("/models/{modelId}")
    @Operation(summary = "One model's stage counts, per branch, with the reconciliation check")
    public ModelPipelineDto model(@PathVariable Long modelId) {
        return service.pipelineForModel(modelId);
    }

    @GetMapping("/models")
    @Operation(summary = "Stage counts for every model")
    public List<ModelPipelineDto> allModels() {
        return service.pipelineForAllModels();
    }

    @PostMapping("/receive")
    @Operation(summary = "Record that N pieces were received from a branch")
    public ModelPipelineDto receive(@Valid @RequestBody ReceiveRequest request) {
        return service.receive(request);
    }

    @PostMapping("/sell")
    @Operation(summary = "Move received pieces to sold; flagged pieces are non-sellable")
    public ModelPipelineDto sell(@Valid @RequestBody SellRequest request) {
        return service.sell(request);
    }

    @PostMapping("/move")
    @Operation(summary = "Move pieces between two named stages")
    public ModelPipelineDto move(@Valid @RequestBody StageMoveRequest request) {
        return service.move(request);
    }

    @PostMapping("/flag")
    @Operation(summary = "Mark pieces defective; permitted from the RECEIVED stage onwards")
    public ModelPipelineDto flag(@Valid @RequestBody FlagRequest request) {
        return service.flag(request);
    }

    @PostMapping("/unflag")
    @Operation(summary = "Clear a defect mark")
    public ModelPipelineDto unflag(@Valid @RequestBody FlagRequest request) {
        return service.unflag(request);
    }
}
