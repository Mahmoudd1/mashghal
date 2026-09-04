package com.apparel.tracking.fabric.web;

import java.util.List;

import com.apparel.tracking.fabric.dto.FabricRollDto;
import com.apparel.tracking.fabric.repository.FabricRollRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Individual rolls. They exist only from the moment one reaches a cutting table. */
@RestController
@RequestMapping("/api/rolls")
@Tag(name = "Fabric rolls")
public class FabricRollController {

    private final FabricRollRepository rolls;

    public FabricRollController(FabricRollRepository rolls) {
        this.rolls = rolls;
    }

    @GetMapping("/open")
    @Operation(summary = "Rolls left part-used, which a later cut can pick up again")
    @Transactional(readOnly = true)
    public List<FabricRollDto> open(
            @RequestParam(required = false) Long fabricTypeId,
            @RequestParam(required = false) Boolean derbyOnly) {
        return rolls.findOpenRolls(fabricTypeId, derbyOnly).stream().map(FabricRollDto::from).toList();
    }
}
