package com.apparel.tracking.production.service;

import java.math.BigDecimal;
import java.util.List;

import com.apparel.tracking.audit.service.AuditService;
import com.apparel.tracking.common.exception.BusinessRuleException;
import com.apparel.tracking.common.exception.NotFoundException;
import com.apparel.tracking.fabric.domain.FabricColor;
import com.apparel.tracking.fabric.domain.FabricIntake;
import com.apparel.tracking.fabric.domain.FabricRoll;
import com.apparel.tracking.fabric.repository.FabricColorRepository;
import com.apparel.tracking.fabric.repository.FabricIntakeRepository;
import com.apparel.tracking.fabric.repository.FabricRollRepository;
import com.apparel.tracking.production.domain.Cut;
import com.apparel.tracking.production.domain.CutRoll;
import com.apparel.tracking.production.domain.CutType;
import com.apparel.tracking.production.dto.CutRollDto;
import com.apparel.tracking.production.dto.CutRollRequest;
import com.apparel.tracking.production.repository.CutRollRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The roll consumption lifecycle.
 *
 * <p>Two counters on the parent batch move on different schedules, and keeping
 * them apart is the whole point of this class:
 *
 * <ul>
 *   <li><b>Weight</b> falls every time fabric comes off a roll, finished or not.</li>
 *   <li><b>Roll count</b> falls exactly once per roll, at the moment it is
 *       finished — which may be a later cut than the one that first used it.</li>
 * </ul>
 *
 * <p>Getting that wrong in either direction is the failure mode the spec warns
 * about: decrementing the count on every use over-depletes the batch, never
 * decrementing it leaves rolls that no longer exist on the books. Every mutation
 * here therefore reverses its own previous effect before applying the new one,
 * so edits and deletes cannot leave a half-applied change behind.
 */
@Service
@Transactional
public class CutRollService {

    private final CutRollRepository cutRolls;
    private final FabricRollRepository rolls;
    private final FabricIntakeRepository intakes;
    private final FabricColorRepository colors;
    private final AuditService audit;

    public CutRollService(
            CutRollRepository cutRolls,
            FabricRollRepository rolls,
            FabricIntakeRepository intakes,
            FabricColorRepository colors,
            AuditService audit) {
        this.cutRolls = cutRolls;
        this.rolls = rolls;
        this.intakes = intakes;
        this.colors = colors;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<CutRollDto> listForCut(Long cutId) {
        return cutRolls.findByCut(cutId).stream().map(CutRollDto::from).toList();
    }

    /**
     * Puts a roll on a cut, or adjusts one already there.
     *
     * <p>The roll is either an existing part-used one being picked up again, or a
     * fresh one drawn off a dated batch and weighed at the table.
     */
    public CutRollDto addOrUpdate(Cut cut, CutRollRequest request) {
        cut.requireOpen();

        FabricRoll roll = resolveRoll(cut, request);
        var existing = cutRolls.findByCutIdAndFabricRollId(cut.getId(), roll.getId());

        // Undo whatever this line previously did before applying the new figures,
        // so an edit is never a delta on top of a stale state.
        existing.ifPresent(this::reverse);

        BigDecimal weightAtStart = existing
                .map(CutRoll::getWeightAtStart)
                .orElse(roll.getRemainingWeight());

        CutRoll line = existing.orElseGet(() -> {
            CutRoll created = new CutRoll();
            created.setCut(cut);
            created.setFabricRoll(roll);
            return created;
        });

        line.setLayers(request.layers());
        line.setDefectWeight(request.defectWeight() == null ? BigDecimal.ZERO : request.defectWeight());
        line.setNote(request.note());
        line.applyWeights(weightAtStart, request.weightUsed(), request.done());

        if (line.getDefectWeight().compareTo(line.getWeightConsumed()) > 0) {
            throw new BusinessRuleException("defect_exceeds_consumed",
                    "Waste of %s cannot exceed the %s consumed from the roll"
                            .formatted(line.getDefectWeight(), line.getWeightConsumed()));
        }

        apply(line);
        CutRoll saved = existing.isPresent() ? line : cutRolls.save(line);

        audit.record(AuditService.FABRIC_ALLOCATED, "CutRoll", saved.getId(), cut.getBranch(),
                saved.getWeightConsumed(),
                "Cut %s, roll %s, %d layers%s".formatted(
                        cut.getCutNumber(), roll.displayName(), saved.getLayers(),
                        saved.isDone() ? ", finished" : ", left open"));

        return CutRollDto.from(saved);
    }

    public void remove(Long cutRollId) {
        CutRoll line = cutRolls.findById(cutRollId)
                .orElseThrow(() -> NotFoundException.of("Cut roll", cutRollId));
        line.getCut().requireOpen();

        reverse(line);
        audit.record(AuditService.FABRIC_RELEASED, "CutRoll", cutRollId, line.getCut().getBranch(),
                line.getWeightConsumed(), null);

        // A roll only conjured into existence for this line has nothing left to say.
        FabricRoll roll = line.getFabricRoll();
        cutRolls.delete(line);
        if (!cutRolls.existsByFabricRollId(roll.getId())) {
            rolls.delete(roll);
        }
    }

    // --- the two-dimensional bookkeeping -------------------------------------

    /**
     * Applies a line: weight always, roll count only when it finishes the roll.
     *
     * <p>Two things come off the roll, not one. What the cut used is consumption;
     * what was still on the roll when the cut closed it is waste. Both leave the
     * batch, but they leave by different doors, so that "consumed" keeps meaning
     * fabric that became garments.
     */
    private void apply(CutRoll line) {
        FabricRoll roll = line.getFabricRoll();
        FabricIntake intake = roll.getIntake();

        BigDecimal available = roll.getRemainingWeight();
        BigDecimal offTheRoll = line.weightOffTheRoll();
        if (offTheRoll.compareTo(available) > 0) {
            throw new BusinessRuleException("roll_insufficient_weight",
                    "Roll %s holds %s, cannot take %s off it"
                            .formatted(roll.displayName(), available, offTheRoll));
        }

        // Finishing a roll takes its whole balance, so this lands on zero of its
        // own accord — no need to force it, which is what lets reverse() undo it.
        roll.setRemainingWeight(available.subtract(offTheRoll));
        intake.consumeWeight(line.getWeightConsumed());
        if (line.getWasteWeight().signum() > 0) {
            intake.wasteWeight(line.getWasteWeight());
        }

        if (line.isDone()) {
            roll.setClosed(true);
            // The one and only moment the batch's roll count moves.
            intake.consumeRoll();
        }
    }

    /** Exact inverse of {@link #apply}, so edits and deletes leave no residue. */
    private void reverse(CutRoll line) {
        FabricRoll roll = line.getFabricRoll();
        FabricIntake intake = roll.getIntake();

        if (line.isDone()) {
            roll.setClosed(false);
            intake.releaseRoll();
        }
        roll.setRemainingWeight(roll.getRemainingWeight().add(line.weightOffTheRoll()));
        intake.releaseWeight(line.getWeightConsumed());
        if (line.getWasteWeight().signum() > 0) {
            intake.releaseWaste(line.getWasteWeight());
        }
    }

    // --- helpers -------------------------------------------------------------

    private FabricRoll resolveRoll(Cut cut, CutRollRequest request) {
        if (request.fabricRollId() != null) {
            FabricRoll roll = rolls.findById(request.fabricRollId())
                    .orElseThrow(() -> NotFoundException.of("Fabric roll", request.fabricRollId()));
            // A roll already on this cut may be edited even once finished.
            if (cutRolls.findByCutIdAndFabricRollId(cut.getId(), roll.getId()).isEmpty()) {
                roll.requireOpen();
            }
            requireMatchesCut(cut, roll.getIntake());
            return roll;
        }

        if (request.fabricIntakeId() == null || request.initialWeight() == null) {
            throw new BusinessRuleException("roll_source_required",
                    "Either pick an open roll, or name the batch and the roll's weighed quantity");
        }

        FabricIntake intake = intakes.findById(request.fabricIntakeId())
                .orElseThrow(() -> NotFoundException.of("Fabric intake", request.fabricIntakeId()));
        requireMatchesCut(cut, intake);

        if (intake.remainingRolls() <= 0) {
            throw new BusinessRuleException("intake_insufficient_rolls",
                    "The %s batch has no rolls left".formatted(intake.getIntakeDate()));
        }

        FabricRoll roll = new FabricRoll();
        roll.setIntake(intake);
        roll.setColor(resolveColor(request.fabricColorId(), intake));
        roll.setLabel(request.rollLabel());
        roll.setInitialWeight(request.initialWeight());
        roll.setRemainingWeight(request.initialWeight());
        return rolls.save(roll);
    }

    /**
     * A cut lays out one fabric type, and its type decides which pool it may draw
     * from: derby stock for a DERBY cut, regular stock for anything else.
     */
    private void requireMatchesCut(Cut cut, FabricIntake intake) {
        if (cut.getFabricType() != null
                && !cut.getFabricType().getId().equals(intake.getFabricType().getId())) {
            throw new BusinessRuleException("roll_wrong_fabric_type",
                    "Cut %s is laid out in '%s'; this roll is '%s'".formatted(
                            cut.getCutNumber(),
                            cut.getFabricType().getNameAr(),
                            intake.getFabricType().getNameAr()));
        }

        boolean derbyCut = cut.getCutType() == CutType.DERBY;
        if (derbyCut && !intake.isDerbyPool()) {
            throw new BusinessRuleException("derby_cut_needs_derby_stock",
                    "Cut %s is a derby cut and must draw from the derby of '%s', not its regular stock"
                            .formatted(cut.getCutNumber(), intake.getFabricType().getNameAr()));
        }
        if (!derbyCut && intake.isDerbyPool()) {
            throw new BusinessRuleException("regular_cut_cannot_use_derby",
                    "Cut %s is a %s cut and cannot draw from the derby of '%s'"
                            .formatted(cut.getCutNumber(), cut.getCutType(), intake.getFabricType().getNameAr()));
        }
    }

    private FabricColor resolveColor(Long colorId, FabricIntake intake) {
        if (colorId == null) {
            return null;
        }
        FabricColor color = colors.findById(colorId)
                .orElseThrow(() -> NotFoundException.of("Fabric colour", colorId));
        if (!color.getFabricType().getId().equals(intake.getFabricType().getId())) {
            throw new BusinessRuleException("color_wrong_fabric_type",
                    "'%s' is not a colour of this batch's fabric type".formatted(color.getNameAr()));
        }
        return color;
    }
}
