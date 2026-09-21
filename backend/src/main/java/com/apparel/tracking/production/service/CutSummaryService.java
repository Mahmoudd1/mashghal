package com.apparel.tracking.production.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.apparel.tracking.common.exception.BusinessRuleException;
import com.apparel.tracking.common.exception.NotFoundException;
import com.apparel.tracking.fabric.domain.FabricColor;
import com.apparel.tracking.fabric.domain.FabricIntake;
import com.apparel.tracking.fabric.domain.FabricIntakeColor;
import com.apparel.tracking.production.domain.Cut;
import com.apparel.tracking.production.domain.CutColorLine;
import com.apparel.tracking.production.domain.CutFabricDraw;
import com.apparel.tracking.production.domain.CutType;
import com.apparel.tracking.production.dto.CutColorLineRequest;
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
 * <p>A run cut by colour takes that walk once per colour, down a narrower shelf:
 * only the batches holding the colour, each giving at most what it holds of it.
 * Derby is always written up that way — it is bought, kept and asked for by
 * colour — and a secondary run may be, instead of spending its main cut's weight.
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
     * One walk down the shelf: a colour and its weight, or the whole run when it
     * is not cut by colour.
     */
    private record Line(FabricColor color, BigDecimal weight, int newRolls) {}

    /** One batch's share of one line, worked out but not yet taken. */
    private record Planned(
            FabricIntake intake, FabricColor color, BigDecimal consumed, BigDecimal waste, int rolls) {}

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
            List<CutColorLineRequest> colorLines) {

        List<Line> lines = colorLines.isEmpty()
                ? List.of(new Line(null, totalWeight, newRolls))
                : colorLines.stream()
                        .map(request -> new Line(
                                requireColor(request.fabricColorId()),
                                request.weight(),
                                request.rollsOrZero() - request.reusedOrZero()))
                        .toList();

        return plan(fabricTypeId, cutType, lines, waste, null).stream()
                .map(planned -> new CutFabricDrawDto(
                        null,
                        planned.intake().getId(),
                        planned.intake().getIntakeDate(),
                        planned.intake().getSupplier() == null
                                ? null : planned.intake().getSupplier().getNameAr(),
                        planned.color() == null ? null : planned.color().getNameAr(),
                        planned.consumed(),
                        planned.waste(),
                        planned.rolls()))
                .toList();
    }

    /** Draws the cut's fabric off the batches and records where it came from. */
    public void apply(Cut cut) {
        if (!cut.isSummary()) {
            return;
        }
        // A secondary run not cut by colour is cut from what the main run already
        // took off the shelf — the remnants of its own rolls. Drawing again would
        // take the same fabric from the batches twice, so this only checks that
        // the main cut is big enough to have covered it.
        if (cut.getCutType() == CutType.SECONDARY && !cut.drawsByColor()) {
            requireParentCovers(cut);
            return;
        }
        if (cut.getFabricType() == null) {
            throw new BusinessRuleException("cut_summary_needs_fabric_type",
                    "A cut recorded from its totals must say which fabric it laid out");
        }

        List<Line> lines = cut.drawsByColor()
                ? cut.getColorLines().stream()
                        .map(line -> new Line(line.getColor(), line.getWeight(), line.newRolls()))
                        .toList()
                : List.of(new Line(null, cut.getTotalWeight(), cut.newRolls()));

        List<Planned> plan = plan(
                cut.getFabricType().getId(), cut.getCutType(), lines, cut.getWasteWeight(), cut.getId());

        for (Planned planned : plan) {
            FabricIntake batch = planned.intake();
            if (planned.consumed().signum() > 0) {
                batch.consumeWeight(planned.consumed());
            }
            if (planned.waste().signum() > 0) {
                batch.wasteWeight(planned.waste());
            }
            batch.consumeRolls(planned.rolls());

            CutFabricDraw draw = new CutFabricDraw();
            draw.setCut(cut);
            draw.setIntake(batch);
            draw.setFabricColor(planned.color());
            draw.setWeightConsumed(planned.consumed());
            draw.setWasteWeight(planned.waste());
            draw.setRollCount(planned.rolls());
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
     * Records the colours this run was cut in, one line each.
     *
     * <p>Derby is bought, kept and asked for by colour, so a derby run is written
     * up colour by colour. A secondary run may be, and is then drawn from the
     * shelf that way instead of being spent from the main cut it hangs off.
     *
     * <p>A line already on the cut is kept and changed in place rather than
     * replaced, so an edit never deletes and re-inserts the same colour — which
     * the one-line-per-colour key would refuse, since inserts are flushed first.
     */
    public void assignColorLines(Cut cut, List<CutColorLineRequest> requests) {
        if (!cut.isSummary() || requests.isEmpty()) {
            if (cut.isSummary() && cut.getCutType() == CutType.DERBY) {
                throw new BusinessRuleException("cut_derby_color_required",
                        "Say which colour of derby this run was cut in");
            }
            cut.getColorLines().clear();
            return;
        }

        Set<Long> seen = new HashSet<>();
        for (CutColorLineRequest request : requests) {
            if (!seen.add(request.fabricColorId())) {
                throw new BusinessRuleException("cut_color_duplicate",
                        "Each colour goes on one line; that colour is on two");
            }
        }

        Map<Long, CutColorLine> existing = new LinkedHashMap<>();
        for (CutColorLine line : cut.getColorLines()) {
            existing.put(line.getColor().getId(), line);
        }

        List<CutColorLine> lines = new ArrayList<>();
        for (CutColorLineRequest request : requests) {
            FabricColor color = requireColor(request.fabricColorId());
            requireSameFabric(cut, color);

            if (request.reusedOrZero() > request.rollsOrZero()) {
                throw new BusinessRuleException("cut_summary_reused_exceeds_total",
                        "%d rolls of %s were already open, which is more than the %d on the line"
                                .formatted(request.reusedOrZero(), color.getNameAr(),
                                        request.rollsOrZero()));
            }

            CutColorLine line = existing.remove(color.getId());
            if (line == null) {
                line = new CutColorLine();
                line.setCut(cut);
                line.setColor(color);
            }
            line.setWeight(request.weight());
            line.setTotalRolls(request.rollsOrZero());
            line.setReusedRolls(request.reusedOrZero());
            lines.add(line);
        }

        cut.getColorLines().retainAll(lines);
        for (CutColorLine line : lines) {
            if (!cut.getColorLines().contains(line)) {
                cut.getColorLines().add(line);
            }
        }
    }

    /**
     * A colour belongs to a fabric type, so naming one names the fabric with it:
     * a run that never said which fabric it laid out learns it here rather than
     * being asked the same thing twice.
     */
    private void requireSameFabric(Cut cut, FabricColor color) {
        if (cut.getFabricType() == null) {
            cut.setFabricType(color.getFabricType());
        } else if (!cut.getFabricType().getId().equals(color.getFabricType().getId())) {
            throw new BusinessRuleException("cut_color_wrong_fabric",
                    "%s is a colour of %s, not of the fabric this run lays out"
                            .formatted(color.getNameAr(), color.getFabricType().getNameAr()));
        }
    }

    /**
     * Works out which batch gives what to each line, without taking anything.
     *
     * <p>The run's عجز is split across its lines by weight, then each line's
     * share across the batches it lands on. Lines are walked in turn, and what an
     * earlier line has planned off a batch is taken out of that batch's headroom
     * before the next looks at it — two colours can come off one purchase, and
     * the second must see what the first left.
     */
    private List<Planned> plan(
            Long fabricTypeId, CutType cutType, List<Line> lines, BigDecimal waste, Long excludeCutId) {

        BigDecimal runWaste = waste == null ? BigDecimal.ZERO : waste;
        List<BigDecimal> lineWaste = FabricAllocator.splitByWeight(
                lines.stream().map(Line::weight).toList(), runWaste);

        Map<Long, BigDecimal> weightPlanned = new HashMap<>();
        Map<Long, Integer> rollsPlanned = new HashMap<>();
        List<Planned> planned = new ArrayList<>();

        for (int index = 0; index < lines.size(); index++) {
            Line line = lines.get(index);

            List<FabricAllocator.Headroom> headroom =
                    headroomFor(fabricTypeId, cutType, line.color(), excludeCutId).stream()
                            .map(room -> {
                                Long id = room.intake().getId();
                                return new FabricAllocator.Headroom(
                                        room.intake(),
                                        room.weight()
                                                .subtract(weightPlanned.getOrDefault(id, BigDecimal.ZERO))
                                                .max(BigDecimal.ZERO),
                                        Math.max(0, room.rolls() - rollsPlanned.getOrDefault(id, 0)));
                            })
                            .toList();

            List<FabricAllocator.Share> shares = FabricAllocator.allocate(
                    headroom, line.weight(), line.newRolls(), shortageLabel(line.color()));
            List<BigDecimal> shareWaste = FabricAllocator.splitWaste(shares, lineWaste.get(index));

            for (int share = 0; share < shares.size(); share++) {
                FabricAllocator.Share taken = shares.get(share);
                BigDecimal binned = shareWaste.get(share);
                planned.add(new Planned(
                        taken.intake(), line.color(), taken.weight().subtract(binned), binned, taken.rolls()));

                Long id = taken.intake().getId();
                weightPlanned.merge(id, taken.weight(), BigDecimal::add);
                rollsPlanned.merge(id, taken.rolls(), Integer::sum);
            }
        }
        return planned;
    }

    /**
     * What each batch may give one line, oldest first.
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

    private FabricColor requireColor(Long fabricColorId) {
        return colors.findById(fabricColorId)
                .orElseThrow(() -> NotFoundException.of("Fabric colour", fabricColorId));
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
