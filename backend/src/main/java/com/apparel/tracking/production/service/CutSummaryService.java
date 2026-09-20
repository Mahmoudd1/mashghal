package com.apparel.tracking.production.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.apparel.tracking.common.exception.BusinessRuleException;
import com.apparel.tracking.common.exception.NotFoundException;
import com.apparel.tracking.fabric.domain.FabricColor;
import com.apparel.tracking.fabric.domain.FabricIntake;
import com.apparel.tracking.fabric.domain.FabricIntakeColor;
import com.apparel.tracking.production.domain.Cut;
import com.apparel.tracking.production.domain.CutFabricDraw;
import com.apparel.tracking.production.domain.CutType;
import com.apparel.tracking.production.dto.CutFabricDrawDto;
import com.apparel.tracking.production.repository.CutFabricDrawRepository;
import com.apparel.tracking.production.repository.CutRepository;
import com.apparel.tracking.fabric.repository.FabricIntakeRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The fabric side of a cut recorded from its totals.
 *
 * <p>Where {@link CutRollService} moves stock one roll at a time, this moves it
 * in one go and infers which batches it came from: oldest first, spilling into
 * the next as each empties. The result is written down rather than kept in the
 * head, because reversing a draw has to give the fabric back to the very batches
 * it came from — and by then those batches hold something different, so the same
 * calculation would land somewhere else.
 *
 * <p>Inferring it is the fallback, not the rule: a run that knows which purchase
 * its fabric came off names the batch and the colour outright, and then the whole
 * weight comes off that one batch. Derby always does — it is bought and asked for
 * by colour, so guessing at the oldest batch would charge the wrong purchase — and
 * a secondary run may, instead of spending its main cut's weight.
 *
 * <p>Every mutation reverses its own previous effect before applying the new
 * one, exactly as the roll-by-roll path does, so an edit is never a delta on top
 * of a stale state.
 */
@Service
@Transactional
public class CutSummaryService {

    private final CutFabricDrawRepository draws;
    private final FabricIntakeRepository intakes;
    private final CutRepository cuts;

    public CutSummaryService(
            CutFabricDrawRepository draws, FabricIntakeRepository intakes, CutRepository cuts) {
        this.draws = draws;
        this.intakes = intakes;
        this.cuts = cuts;
    }

    /**
     * Works out the allocation without touching anything.
     *
     * <p>What the form shows while the totals are being typed, so the batches
     * about to be emptied are visible before saving rather than afterwards — and
     * so "there is not enough of this fabric" arrives as a preview rather than a
     * rejected submit.
     */
    @Transactional(readOnly = true)
    public List<CutFabricDrawDto> preview(
            Long fabricTypeId, CutType cutType, BigDecimal totalWeight, BigDecimal waste, int newRolls,
            Long fabricIntakeId, Long fabricColorId) {

        // A run that names its batch has nothing to allocate: the preview is that
        // one batch, and what it is for is the headroom check it runs on the way.
        if (fabricIntakeId != null) {
            FabricIntake batch = requireBatch(fabricIntakeId);
            requireBatchCovers(batch, totalWeight, newRolls);
            requireColorCovers(batch, fabricColorId, totalWeight, null);
            return List.of(new CutFabricDrawDto(
                    null,
                    batch.getId(),
                    batch.getIntakeDate(),
                    batch.getSupplier() == null ? null : batch.getSupplier().getNameAr(),
                    totalWeight.subtract(waste),
                    waste,
                    newRolls));
        }

        List<FabricAllocator.Share> shares = FabricAllocator.allocate(
                batchesFor(fabricTypeId, cutType), totalWeight, newRolls);
        List<BigDecimal> wasteSplit = FabricAllocator.splitWaste(shares, waste);

        List<CutFabricDrawDto> rows = new ArrayList<>();
        for (int index = 0; index < shares.size(); index++) {
            FabricAllocator.Share share = shares.get(index);
            BigDecimal shareWaste = wasteSplit.get(index);
            rows.add(new CutFabricDrawDto(
                    null,
                    share.intake().getId(),
                    share.intake().getIntakeDate(),
                    share.intake().getSupplier() == null
                            ? null : share.intake().getSupplier().getNameAr(),
                    share.weight().subtract(shareWaste),
                    shareWaste,
                    share.rolls()));
        }
        return rows;
    }

    /** Draws the cut's fabric off the batches and records where it came from. */
    public void apply(Cut cut) {
        if (!cut.isSummary()) {
            return;
        }
        // Named its batch: it says where its fabric came from, so there is nothing
        // to infer and nothing to spread across purchases.
        if (cut.drawsFromNamedBatch()) {
            drawFromNamedBatch(cut);
            return;
        }
        // A secondary run is cut from what the main run already took off the
        // shelf — the remnants of its own rolls. Drawing again would take the
        // same fabric from the batches twice, so this only checks that the main
        // cut is big enough to have covered it.
        if (cut.getCutType() == CutType.SECONDARY) {
            requireParentCovers(cut);
            return;
        }
        if (cut.getFabricType() == null) {
            throw new BusinessRuleException("cut_summary_needs_fabric_type",
                    "A cut recorded from its totals must say which fabric it laid out");
        }

        List<FabricAllocator.Share> shares = FabricAllocator.allocate(
                batchesFor(cut.getFabricType().getId(), cut.getCutType()),
                cut.getTotalWeight(),
                cut.newRolls());
        List<BigDecimal> wasteSplit = FabricAllocator.splitWaste(shares, cut.getWasteWeight());

        for (int index = 0; index < shares.size(); index++) {
            FabricAllocator.Share share = shares.get(index);
            FabricIntake batch = share.intake();
            BigDecimal shareWaste = wasteSplit.get(index);
            BigDecimal consumed = share.weight().subtract(shareWaste);

            if (consumed.signum() > 0) {
                batch.consumeWeight(consumed);
            }
            if (shareWaste.signum() > 0) {
                batch.wasteWeight(shareWaste);
            }
            batch.consumeRolls(share.rolls());

            CutFabricDraw draw = new CutFabricDraw();
            draw.setCut(cut);
            draw.setIntake(batch);
            draw.setWeightConsumed(consumed);
            draw.setWasteWeight(shareWaste);
            draw.setRollCount(share.rolls());
            draws.save(draw);
        }
    }

    /**
     * Exact inverse of {@link #apply}, replayed from what was written down.
     *
     * <p>Never recalculated: the batches have moved on, and working the
     * allocation out again would return the fabric to the wrong purchases.
     */
    public void reverse(Cut cut) {
        List<CutFabricDraw> recorded = draws.findByCutId(cut.getId());
        for (CutFabricDraw draw : recorded) {
            FabricIntake batch = draw.getIntake();
            if (draw.getWeightConsumed().signum() > 0) {
                batch.releaseWeight(draw.getWeightConsumed());
            }
            if (draw.getWasteWeight().signum() > 0) {
                batch.releaseWaste(draw.getWasteWeight());
            }
            batch.releaseRolls(draw.getRollCount());
        }
        draws.deleteAll(recorded);
    }

    /**
     * Records which batch, and which colour of it, this run was cut from.
     *
     * <p>Derby leaves the shelf by colour — "the navy from the 12/07 batch" — so
     * a derby run says outright where its fabric came from rather than having it
     * guessed oldest-batch-first, which would charge the wrong purchase and the
     * wrong money. A secondary run may name a batch too, and then it is spent
     * from that batch instead of from the main cut it hangs off.
     */
    public void assignSource(Cut cut, Long fabricIntakeId, Long fabricColorId) {
        if (!cut.isSummary() || fabricIntakeId == null) {
            if (cut.isSummary() && cut.getCutType() == CutType.DERBY) {
                throw new BusinessRuleException("cut_derby_batch_required",
                        "Say which derby batch, and which colour of it, this run was cut from");
            }
            cut.setFabricIntake(null);
            cut.setFabricColor(null);
            return;
        }

        FabricIntake batch = requireBatch(fabricIntakeId);

        // A derby run empties the derby and anything else the regular stock — the
        // same crossing the oldest-first and roll-by-roll paths both refuse.
        boolean wantsDerby = cut.getCutType() == CutType.DERBY;
        if (batch.isDerbyPool() != wantsDerby) {
            throw new BusinessRuleException("cut_batch_wrong_pool",
                    wantsDerby
                            ? "A derby run must be cut from a derby batch"
                            : "The %s batch is derby, and only a derby run lays that out"
                                    .formatted(batch.getIntakeDate()));
        }

        // Naming the batch names the fabric with it, so a run that never said
        // which fabric it laid out learns it here rather than being asked twice.
        if (cut.getFabricType() == null) {
            cut.setFabricType(batch.getFabricType());
        } else if (!cut.getFabricType().getId().equals(batch.getFabricType().getId())) {
            throw new BusinessRuleException("cut_batch_wrong_fabric",
                    "The %s batch is %s, not the fabric this run lays out"
                            .formatted(batch.getIntakeDate(), batch.getFabricType().getNameAr()));
        }

        cut.setFabricIntake(batch);
        cut.setFabricColor(resolveColor(batch, fabricColorId));
    }

    /**
     * Takes the run's whole weight off the one batch it names.
     *
     * <p>Written down exactly as an inferred allocation is, and for the same
     * reason: the reversal has to put the counters back on that batch, and a
     * later edit must not have to work out where they went.
     */
    private void drawFromNamedBatch(Cut cut) {
        FabricIntake batch = cut.getFabricIntake();
        BigDecimal waste = cut.getWasteWeight() == null ? BigDecimal.ZERO : cut.getWasteWeight();
        BigDecimal consumed = cut.getTotalWeight().subtract(waste);

        requireColorCovers(batch, colorIdOf(cut), cut.getTotalWeight(), cut.getId());

        if (consumed.signum() > 0) {
            batch.consumeWeight(consumed);
        }
        if (waste.signum() > 0) {
            batch.wasteWeight(waste);
        }
        batch.consumeRolls(cut.newRolls());

        CutFabricDraw draw = new CutFabricDraw();
        draw.setCut(cut);
        draw.setIntake(batch);
        draw.setWeightConsumed(consumed);
        draw.setWasteWeight(waste);
        draw.setRollCount(cut.newRolls());
        draws.save(draw);
    }

    /**
     * The colour of the batch this run took.
     *
     * <p>Asked for as soon as the batch says which colours it holds: "50 kg off
     * the 12/07 batch" without saying which colour is the vague record this
     * replaces. A batch whose colours were never written down has none to pick
     * from, and is named on its own.
     */
    private FabricColor resolveColor(FabricIntake batch, Long fabricColorId) {
        List<FabricIntakeColor> breakdown = batch.getColorBreakdown();
        if (breakdown.isEmpty()) {
            return null;
        }
        if (fabricColorId == null) {
            throw new BusinessRuleException("cut_batch_color_required",
                    "Say which colour of the %s batch this run was cut from"
                            .formatted(batch.getIntakeDate()));
        }
        return breakdown.stream()
                .map(FabricIntakeColor::getColor)
                .filter(color -> color.getId().equals(fabricColorId))
                .findFirst()
                .orElseThrow(() -> new BusinessRuleException("cut_batch_color_not_in_batch",
                        "The %s batch holds no fabric of that colour"
                                .formatted(batch.getIntakeDate())));
    }

    /**
     * Keeps the runs cut from one colour inside what the batch says it holds of
     * it.
     *
     * <p>Only where the breakdown carries a weight for that colour: the
     * breakdown is a soft record, and a colour given as a roll count says
     * nothing about how many kilos of it are on the shelf. What the batch holds
     * altogether is checked by the batch itself, either way.
     */
    private void requireColorCovers(
            FabricIntake batch, Long fabricColorId, BigDecimal wanted, Long excludeCutId) {
        if (fabricColorId == null) {
            return;
        }
        BigDecimal held = batch.getColorBreakdown().stream()
                .filter(row -> row.getColor().getId().equals(fabricColorId))
                .map(FabricIntakeColor::getQuantity)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        if (held == null) {
            return;
        }

        BigDecimal taken = draws.weightTakenOfColor(batch.getId(), fabricColorId, excludeCutId);
        if (taken.add(wanted).compareTo(held) > 0) {
            throw new BusinessRuleException("cut_batch_color_insufficient",
                    "The %s batch holds %s of that colour, of which %s is already cut"
                            .formatted(batch.getIntakeDate(), held, taken));
        }
    }

    /** What a preview would take, against what the batch still has. */
    private void requireBatchCovers(FabricIntake batch, BigDecimal wanted, int newRolls) {
        if (wanted.compareTo(batch.remainingQuantity()) > 0) {
            throw new BusinessRuleException("intake_insufficient_quantity",
                    "The %s batch has %s left, cannot take %s"
                            .formatted(batch.getIntakeDate(), batch.remainingQuantity(), wanted));
        }
        if (newRolls > batch.remainingRolls()) {
            throw new BusinessRuleException("intake_insufficient_rolls",
                    "The %s batch has %d rolls left, cannot take %d"
                            .formatted(batch.getIntakeDate(), batch.remainingRolls(), newRolls));
        }
    }

    private FabricIntake requireBatch(Long fabricIntakeId) {
        return intakes.findById(fabricIntakeId)
                .orElseThrow(() -> NotFoundException.of("Fabric batch", fabricIntakeId));
    }

    private Long colorIdOf(Cut cut) {
        return cut.getFabricColor() == null ? null : cut.getFabricColor().getId();
    }

    /**
     * Checks a secondary run against the main cut it is spent from.
     *
     * <p>The main cut's weight has to cover every secondary run hanging off it.
     * Beyond that there is nothing to enforce: what the main run kept for itself
     * is simply what its children did not take.
     */
    private void requireParentCovers(Cut cut) {
        Cut parent = cut.getParentMainCut();
        if (parent == null) {
            throw new BusinessRuleException("cut_parent_required",
                    "A secondary cut must reference the MAIN cut it is spent from");
        }

        BigDecimal parentTotal = parent.isSummary()
                ? parent.getTotalWeight()
                : cuts.rollWeightOffTheShelf(parent.getId());
        BigDecimal siblings = cuts.secondaryWeightCharged(parent.getId(), cut.getId());
        BigDecimal wanted = siblings.add(cut.getTotalWeight());

        if (parentTotal == null || wanted.compareTo(parentTotal) > 0) {
            throw new BusinessRuleException("cut_secondary_exceeds_parent",
                    ("Cut %s took %s off the shelf; its secondary runs would spend %s of it")
                            .formatted(parent.getCutNumber(),
                                    parentTotal == null ? BigDecimal.ZERO : parentTotal, wanted));
        }
    }

    /** What this cut's secondary runs have charged against it. */
    @Transactional(readOnly = true)
    public BigDecimal chargedByChildren(Long cutId) {
        return cuts.secondaryWeightCharged(cutId, null);
    }

    @Transactional(readOnly = true)
    public List<CutFabricDrawDto> forCut(Long cutId) {
        return draws.findByCutId(cutId).stream()
                .map(CutFabricDrawDto::from)
                .toList();
    }

    /**
     * A derby cut empties the derby, anything else empties the regular stock —
     * the same rule the roll-by-roll path enforces when a roll is picked.
     */
    private List<FabricIntake> batchesFor(Long fabricTypeId, CutType cutType) {
        return intakes.openBatchesOldestFirst(fabricTypeId, cutType == CutType.DERBY);
    }
}
