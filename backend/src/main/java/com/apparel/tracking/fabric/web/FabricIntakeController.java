package com.apparel.tracking.fabric.web;

import java.util.List;

import com.apparel.tracking.fabric.domain.RemainingGrouping;
import com.apparel.tracking.fabric.dto.ColorRollCountDto;
import com.apparel.tracking.fabric.dto.RemainingRowDto;
import com.apparel.tracking.fabric.dto.FabricIntakeColorRequest;
import com.apparel.tracking.fabric.dto.OpenRollRowDto;
import com.apparel.tracking.fabric.dto.FabricIntakeDto;
import com.apparel.tracking.fabric.dto.FabricIntakeRequest;
import com.apparel.tracking.fabric.dto.FabricStockDto;
import com.apparel.tracking.fabric.dto.IntakeRemainingRowDto;
import com.apparel.tracking.fabric.service.FabricIntakeService;

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
@RequestMapping("/api/intakes")
@Tag(name = "Fabric intake")
public class FabricIntakeController {

    private final FabricIntakeService service;

    public FabricIntakeController(FabricIntakeService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Search intake batches; derbyOnly selects the pool")
    public Page<FabricIntakeDto> search(
            @RequestParam(required = false) Long fabricTypeId,
            @RequestParam(required = false) Boolean derbyOnly,
            @RequestParam(defaultValue = "false") boolean inStockOnly,
            @PageableDefault(size = 25, sort = "intakeDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.search(fabricTypeId, derbyOnly, inStockOnly, pageable);
    }

    @GetMapping("/stock")
    @Operation(summary = "Stock per fabric type and pool, with the indicative colour breakdown")
    public List<FabricStockDto> stock(@RequestParam(required = false) Long fabricTypeId) {
        return service.stock(fabricTypeId);
    }

    @GetMapping("/remaining-by-date")
    @Operation(summary = "How many rolls are left from each intake date")
    public List<IntakeRemainingRowDto> remainingByDate(
            @RequestParam(required = false) Long fabricTypeId,
            @RequestParam(defaultValue = "false") boolean inStockOnly) {
        return service.remainingByDate(fabricTypeId, inStockOnly);
    }

    @GetMapping("/remaining")
    @Operation(summary = "Remaining stock per fabric — overall, by intake date, or by supplier")
    public List<RemainingRowDto> remaining(
            @RequestParam(required = false) Long fabricTypeId,
            @RequestParam(defaultValue = "TOTAL") RemainingGrouping groupBy) {
        return service.remaining(fabricTypeId, groupBy);
    }

    @GetMapping("/open-rolls")
    @Operation(summary = "Rolls sitting part-used right now, per fabric type and colour")
    public List<OpenRollRowDto> openRolls(@RequestParam(required = false) Long fabricTypeId) {
        return service.openRolls(fabricTypeId);
    }

    @GetMapping("/rolls-by-color")
    @Operation(summary = "Roll count per colour, overall or for one intake date")
    public List<ColorRollCountDto> rollsByColor(
            @RequestParam(required = false) Long fabricTypeId,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso =
                    org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate intakeDate,
            @RequestParam(defaultValue = "false") boolean openOnly) {
        return service.rollCountByColor(fabricTypeId, intakeDate, openOnly);
    }

    @GetMapping("/{id}")
    public FabricIntakeDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record a purchase: date, roll count, total quantity, price per unit")
    public FabricIntakeDto create(@Valid @RequestBody FabricIntakeRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public FabricIntakeDto update(@PathVariable Long id, @Valid @RequestBody FabricIntakeRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/colors")
    @Operation(summary = "Set one colour's share of the batch; the breakdown need not add up")
    public FabricIntakeDto setColor(@PathVariable Long id, @Valid @RequestBody FabricIntakeColorRequest request) {
        return service.setColorBreakdown(id, request);
    }

    @DeleteMapping("/{id}/colors/{colorId}")
    public FabricIntakeDto removeColor(@PathVariable Long id, @PathVariable Long colorId) {
        return service.removeColorBreakdown(id, colorId);
    }
}
