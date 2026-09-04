package com.apparel.tracking.size.web;

import java.util.List;

import com.apparel.tracking.size.dto.GarmentSizeDto;
import com.apparel.tracking.size.dto.GarmentSizeRequest;
import com.apparel.tracking.size.dto.ModelSizeCategoryRowDto;
import com.apparel.tracking.size.dto.SizeCategoryDto;
import com.apparel.tracking.size.dto.SizeCategoryRequest;
import com.apparel.tracking.size.service.SizeService;

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
@RequestMapping("/api")
@Tag(name = "Sizes")
public class SizeController {

    private final SizeService service;

    public SizeController(SizeService service) {
        this.service = service;
    }

    @GetMapping("/size-categories")
    @Operation(summary = "Size categories with their sizes")
    public List<SizeCategoryDto> categories(@RequestParam(defaultValue = "true") boolean activeOnly) {
        return service.listCategories(activeOnly);
    }

    @GetMapping("/sizes")
    public List<GarmentSizeDto> sizes() {
        return service.listSizes();
    }

    @GetMapping("/size-categories/models")
    @Operation(summary = "Models that have been cut in a given size range")
    public List<ModelSizeCategoryRowDto> modelsByCategory(@RequestParam(required = false) Long categoryId) {
        return service.modelsByCategory(categoryId);
    }

    @PostMapping("/size-categories")
    @ResponseStatus(HttpStatus.CREATED)
    public SizeCategoryDto createCategory(@Valid @RequestBody SizeCategoryRequest request) {
        return service.createCategory(request);
    }

    @PutMapping("/size-categories/{id}")
    public SizeCategoryDto updateCategory(@PathVariable Long id, @Valid @RequestBody SizeCategoryRequest request) {
        return service.updateCategory(id, request);
    }

    @PostMapping("/sizes")
    @ResponseStatus(HttpStatus.CREATED)
    public GarmentSizeDto createSize(@Valid @RequestBody GarmentSizeRequest request) {
        return service.createSize(request);
    }

    @DeleteMapping("/sizes/{id}")
    public ResponseEntity<Void> deleteSize(@PathVariable Long id) {
        service.deleteSize(id);
        return ResponseEntity.noContent().build();
    }
}
