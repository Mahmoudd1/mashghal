package com.apparel.tracking.production.web;

import java.util.List;

import com.apparel.tracking.production.dto.ModelCutsDto;
import com.apparel.tracking.production.dto.ModelDto;
import com.apparel.tracking.production.dto.ModelFabricUsageDto;
import com.apparel.tracking.production.dto.ModelRequest;
import com.apparel.tracking.production.service.ModelService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

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
@RequestMapping("/api/models")
@Tag(name = "Models")
public class ModelController {

    private final ModelService service;

    public ModelController(ModelService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List models with planned quantities derived from cut allocations")
    public List<ModelDto> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public ModelDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @GetMapping("/{id}/cuts")
    @Operation(summary = "Every cut feeding this model, flagging the rare multi-main-cut case")
    public ModelCutsDto cuts(@PathVariable Long id) {
        return service.cutsFeeding(id);
    }

    @GetMapping("/fabric-usage")
    @Operation(summary = "Fabric used per piece, per model and fabric type")
    public List<ModelFabricUsageDto> fabricUsage(@RequestParam(required = false) Long modelId) {
        return service.fabricUsagePerPiece(modelId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ModelDto create(@Valid @RequestBody ModelRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public ModelDto update(@PathVariable Long id, @Valid @RequestBody ModelRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
