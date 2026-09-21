package com.apparel.tracking.production.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

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
import com.apparel.tracking.fabric.repository.FabricColorRepository;
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
    private final FabricColorRepository colors;
    private final CutRepository cuts;

    public CutSummaryService(
            CutFabricDrawRepository draws,
            FabricIntakeRepository intakes,
            FabricColorRepository colors,
            CutRepository cuts) {
        this.draws = draws;
        this.intakes = intakes;
        this.colors = colors;
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
            Long fabricColorId) {

        FabricColor color = fabricColorId == null
                ? null
                : colors.findById(fabricColorId)
                        .orElseThrow(() -> NotFoundException.of("Fabric colour", fabricColorId));

        List<FabricAllocator.Share> shares = FabricAllocator.allocate(
                headroomFor(fabricTypeId, cutType, color, null),
                totalWeight,
                newRolls,
                shortageLabel(color));
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
        // A secondary run with no colour of its own is cut from what the main run
        // already took off the shelf — the remnants of its own rolls. Drawing
        // again would take the same fabric from the batches twice, so this only
        // checks that the main cut is big enough to have covered it.
        if (cut.getCutType() == CutType.SECONDARY && !cut.drawsByColor()) {
            requireParentCovers(cut);
            return;
        }
        if (cut.getFabricType() == null) {
            throw new BusinessRuleException("cut_summary_needs_fabric_type",
                    "A cut recorded from its totals must say which fabric it laid out");
        }

        List<FabricAllocator.Share> shares = FabricAllocator.allocate(
                headroomFor(cut.getFabricType().getId(), cut.getCutType(),
                        cut.getFabricColor(), cut.getId()),
                cut.getTotalWeight(),
                cut.newRolls(),
                shortageLabel(cut.getFabricColor()));
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
     * Records the colour this run was cut in.
     *
     * <p>Derby is bought, kept and asked for by colour, so a derby run says which
     * — and its weight then comes only off the batches holding that colour. A
     * secondary run may name a colour too, and is then drawn from the shelf that
     * way instead of being spent from the main cut it hangs off.
     */
    public void assignSource(Cut cut, Long fabricColorId) {
        if (!cut.isSummary() || fabricColorId == null) {
            if (cut.isSummary() && cut.getCutType() == CutType.DERBY) {
                throw new BusinessRuleException("cut_derby_color_required",
                        "Say which colour of derby this run was cut in");
            }
            cut.setFabricColor(null);
            return;
        }

        FabricColor color = colors.findById(fabricColorId)
                .orElseThrow(() -> NotFoundException.of("Fabric colour", fabricColorId));

        // A colour belongs to a fabric type, so naming one names the fabric with
        // it: a run that never said which fabric it laid out learns it here
        // rather than being asked the same thing twice.
        if (cut.getFabricType() == null) {
            cut.setFabricType(color.getFabricType());
        } else if (!cut.getFabricType().getId().equals(color.getFabricType().getId())) {
            throw new BusinessRuleException("cut_color_wrong_fabric",
                    "%s is a colour of %s, not of the fabric this run lays out"
                            .formatted(color.getNameAr(), color.getFabricType().getNameAr()));
        }

        cut.setFabricColor(color);
    }

    /**
     * What each batch may give this run, oldest first.
     *
     * <p>Without a colour that is everything each batch has left. With one, only
     * the batches that say they hold the colour are walked at all — a batch that
     * never listed it cannot be said to have any — and each gives at most what it
     * holds of it, less what earlier runs in that colour already took. Where the
     * breakdown names the colour without a weight, which it is allowed to do, the
     * batch's own remainder is the only cap there is.
     */
    private List<FabricAllocator.Headroom> headroomFor(
            Long fabricTypeId, CutType cutType, FabricColor color, Long excludeCutId) {

        List<FabricIntake> batches =
                intakes.openBatchesOldestFirst(fabricTypeId, cutType == CutType.DERBY);
        if (color == null) {
            return batches.stream().map(FabricAllocator.Headroom::wholeBatch).toList();
        }

        List<FabricAllocator.Headroom> headroom = new ArrayList<>();
        for (FabricIntake batch : batches) {
            FabricIntakeColor row = batch.getColorBreakdown().stream()
                    .filter(entry -> entry.getColor().getId().equals(color.getId()))
                    .findFirst()
                    .orElse(null);
            if (row == null) {
                continue;
            }

            BigDecimal weight = batch.remainingQuantity();
            if (row.getQuantity() != null) {
                BigDecimal taken =
                        draws.weightTakenOfColor(batch.getId(), color.getId(), excludeCutId);
                weight = weight.min(row.getQuantity().subtract(taken)).max(BigDecimal.ZERO);
            }
            headroom.add(new FabricAllocator.Headroom(batch, weight, batch.remainingRolls()));
        }
        return headroom;
    }

    /** What came up short, for the message: the colour if there is one. */
    private String shortageLabel(FabricColor color) {
        return color == null ? "this fabric" : color.getNameAr();
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
}
