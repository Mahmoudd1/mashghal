package com.apparel.tracking.fabric.web;

import java.util.List;

import com.apparel.tracking.fabric.dto.DerbyDefaultsDto;
import com.apparel.tracking.fabric.dto.DerbyDto;
import com.apparel.tracking.fabric.service.DerbyService;

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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Fabric master data")
public class DerbyController {

    private final DerbyService derbies;

    public DerbyController(DerbyService derbies) {
        this.derbies = derbies;
    }

    @GetMapping("/derbies")
    public List<DerbyDto> list() {
        return derbies.list();
    }

    @GetMapping("/fabric-types/{fabricTypeId}/derby")
    public DerbyDto forFabricType(@PathVariable Long fabricTypeId) {
        return derbies.getForFabricType(fabricTypeId);
    }

    @GetMapping("/fabric-types/{fabricTypeId}/derby-defaults")
    @Operation(summary = "Supplier and price a new derby would inherit from this fabric")
    public DerbyDefaultsDto defaults(@PathVariable Long fabricTypeId) {
        return derbies.defaultsFor(fabricTypeId);
    }

    @PostMapping("/fabric-types/{fabricTypeId}/derby")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Give this fabric type a derby pool with its opening stock; at most one, ever")
    public DerbyDto create(
            @PathVariable Long fabricTypeId, @Valid @RequestBody DerbyService.DerbyRequest request) {
        return derbies.create(fabricTypeId, request);
    }

    @PutMapping("/derbies/{id}")
    public DerbyDto update(
            @PathVariable Long id, @Valid @RequestBody DerbyService.DerbyNoteRequest request) {
        return derbies.update(id, request);
    }

    @DeleteMapping("/derbies/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        derbies.delete(id);
        return ResponseEntity.noContent().build();
    }
}
