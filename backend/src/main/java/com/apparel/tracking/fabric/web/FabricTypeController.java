package com.apparel.tracking.fabric.web;

import java.util.List;

import com.apparel.tracking.fabric.dto.FabricColorDto;
import com.apparel.tracking.fabric.dto.FabricColorRequest;
import com.apparel.tracking.fabric.dto.FabricTypeDto;
import com.apparel.tracking.fabric.dto.FabricTypeRequest;
import com.apparel.tracking.fabric.service.FabricCatalogService;

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
@RequestMapping("/api/fabric-types")
@Tag(name = "Fabric master data")
public class FabricTypeController {

    private final FabricCatalogService catalog;

    public FabricTypeController(FabricCatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    @Operation(summary = "List fabric types with their colours")
    public List<FabricTypeDto> list(@RequestParam(defaultValue = "false") boolean activeOnly) {
        return catalog.listTypes(activeOnly);
    }

    @GetMapping("/{id}")
    public FabricTypeDto get(@PathVariable Long id) {
        return catalog.getType(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FabricTypeDto create(@Valid @RequestBody FabricTypeRequest request) {
        return catalog.createType(request);
    }

    @PutMapping("/{id}")
    public FabricTypeDto update(@PathVariable Long id, @Valid @RequestBody FabricTypeRequest request) {
        return catalog.updateType(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        catalog.deleteType(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/colors")
    public List<FabricColorDto> colors(@PathVariable Long id) {
        return catalog.listColors(id);
    }

    @PostMapping("/{id}/colors")
    @ResponseStatus(HttpStatus.CREATED)
    public FabricColorDto addColor(@PathVariable Long id, @Valid @RequestBody FabricColorRequest request) {
        return catalog.addColor(id, request);
    }
}
