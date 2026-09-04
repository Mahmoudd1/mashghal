package com.apparel.tracking.production.web;

import com.apparel.tracking.production.service.CutService;

import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Allocations are created under their cut; removal addresses them directly. */
@RestController
@RequestMapping("/api")
@Tag(name = "Cuts")
public class AllocationController {

    private final CutService service;

    public AllocationController(CutService service) {
        this.service = service;
    }

    @DeleteMapping("/model-allocations/{id}")
    public ResponseEntity<Void> removeModelAllocation(@PathVariable Long id) {
        service.removeModelAllocation(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/cut-rolls/{id}")
    public ResponseEntity<Void> removeCutRoll(@PathVariable Long id) {
        service.removeRoll(id);
        return ResponseEntity.noContent().build();
    }
}
