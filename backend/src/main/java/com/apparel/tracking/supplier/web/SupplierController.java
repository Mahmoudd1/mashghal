package com.apparel.tracking.supplier.web;

import java.util.List;

import com.apparel.tracking.supplier.dto.FabricPriceRowDto;
import com.apparel.tracking.supplier.dto.SupplierDto;
import com.apparel.tracking.supplier.dto.SupplierRequest;
import com.apparel.tracking.supplier.service.SupplierService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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
@Tag(name = "Suppliers")
public class SupplierController {

    private final SupplierService service;

    public SupplierController(SupplierService service) {
        this.service = service;
    }

    @GetMapping("/suppliers")
    public List<SupplierDto> list(@RequestParam(defaultValue = "false") boolean activeOnly) {
        return service.list(activeOnly);
    }

    @PostMapping("/suppliers")
    @ResponseStatus(HttpStatus.CREATED)
    public SupplierDto create(@Valid @RequestBody SupplierRequest request) {
        return service.create(request);
    }

    @PutMapping("/suppliers/{id}")
    public SupplierDto update(@PathVariable Long id, @Valid @RequestBody SupplierRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/suppliers/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/fabric-prices")
    // The entire response is money, so this is refused outright rather than blanked.
    @PreAuthorize("hasRole('OWNER')")
    @Operation(summary = "Average price per fabric, weighted by quantity; optionally split by supplier")
    public List<FabricPriceRowDto> fabricPrices(
            @RequestParam(required = false) Long fabricTypeId,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(defaultValue = "false") boolean bySupplier) {
        return service.fabricPrices(fabricTypeId, supplierId, bySupplier);
    }
}
