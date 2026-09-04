package com.apparel.tracking.production.web;

import java.util.List;

import com.apparel.tracking.production.domain.CutStatus;
import com.apparel.tracking.production.domain.CutType;
import com.apparel.tracking.production.dto.CutDto;
import com.apparel.tracking.production.dto.CutModelAllocationDto;
import com.apparel.tracking.production.dto.CutModelAllocationRequest;
import com.apparel.tracking.production.dto.CutRequest;
import com.apparel.tracking.production.dto.CutModelSizeRequest;
import com.apparel.tracking.production.dto.CutRollDto;
import com.apparel.tracking.production.dto.CutRollRequest;
import com.apparel.tracking.production.service.CutService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cuts")
@Tag(name = "Cuts")
public class CutController {

    private final CutService service;

    public CutController(CutService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Search cuts by type, status, branch, or the model they feed")
    public Page<CutDto> search(
            @RequestParam(required = false) CutType cutType,
            @RequestParam(required = false) CutStatus status,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) Long modelId,
            @PageableDefault(size = 25, sort = "cutDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.search(cutType, status, branchId, modelId, pageable);
    }

    @GetMapping("/{id}")
    public CutDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @GetMapping("/{id}/models")
    @Operation(summary = "Every model this cut fed")
    public List<CutModelAllocationDto> models(@PathVariable Long id) {
        return service.modelsFedBy(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CutDto create(@Valid @RequestBody CutRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public CutDto update(@PathVariable Long id, @Valid @RequestBody CutRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/close")
    @Operation(summary = "Close the cut so it stops accepting allocations")
    public CutDto close(@PathVariable Long id) {
        return service.close(id);
    }

    @PostMapping("/{id}/reopen")
    public CutDto reopen(@PathVariable Long id) {
        return service.reopen(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/model-allocations")
    @Operation(summary = "Allocate pieces from this cut to a model at a branch")
    public CutModelAllocationDto allocateToModel(
            @PathVariable Long id, @Valid @RequestBody CutModelAllocationRequest request) {
        return service.allocateToModel(id, request);
    }

    @PostMapping("/{id}/rolls")
    @Operation(summary = "Put a roll on this cut, or adjust one already on it")
    public CutRollDto addRoll(@PathVariable Long id, @Valid @RequestBody CutRollRequest request) {
        return service.addRoll(id, request);
    }

    @PostMapping("/{id}/sizes")
    @Operation(summary = "Set pieces-per-layer for one model and size; piece counts derive from this")
    public CutDto setSize(@PathVariable Long id, @Valid @RequestBody CutModelSizeRequest request) {
        return service.setModelSize(id, request);
    }

    @DeleteMapping("/{id}/sizes/{modelId}/{sizeId}")
    public CutDto removeSize(@PathVariable Long id, @PathVariable Long modelId, @PathVariable Long sizeId) {
        return service.removeModelSize(id, modelId, sizeId);
    }
}
