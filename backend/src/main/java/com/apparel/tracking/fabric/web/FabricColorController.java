package com.apparel.tracking.fabric.web;

import com.apparel.tracking.fabric.dto.FabricColorDto;
import com.apparel.tracking.fabric.dto.FabricColorRequest;
import com.apparel.tracking.fabric.service.FabricCatalogService;

import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Colours are created under their fabric type; edits and deletes address them directly. */
@RestController
@RequestMapping("/api/fabric-colors")
@Tag(name = "Fabric master data")
public class FabricColorController {

    private final FabricCatalogService catalog;

    public FabricColorController(FabricCatalogService catalog) {
        this.catalog = catalog;
    }

    @PutMapping("/{id}")
    public FabricColorDto update(@PathVariable Long id, @Valid @RequestBody FabricColorRequest request) {
        return catalog.updateColor(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        catalog.deleteColor(id);
        return ResponseEntity.noContent().build();
    }
}
