package com.apparel.tracking.production.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.apparel.tracking.common.exception.BusinessRuleException;
import com.apparel.tracking.fabric.domain.FabricIntake;

/**
 * Spreads one lump of fabric across the batches it must have come from, oldest
 * first.
 *
 * <p>A summary cut says only "100 kg of cotton and 18 rolls". Which purchases
 * that came out of is not recorded anywhere, so it is inferred the way a store
 * actually empties: the oldest batch goes first, and what it cannot cover spills
 * into the next.
 *
 * <p>Weight and rolls are allocated in the same pass but capped separately,
 * because a batch runs out of them at different rates — one can hold plenty of
 * weight on very few remaining rolls, or the reverse. Tying them together would
 * invent a shortage that is not there.
 *
 * <p>What a batch may give is passed in as {@link Headroom} rather than read off
 * the batch, because it is not always the whole batch. A run cut in one colour
 * takes only what that batch holds of that colour, and spills into the next batch
 * holding it — the same walk, down a narrower shelf.
 *
 * <p>Pure arithmetic: nothing here touches a batch. The caller applies the
 * result, which is also what gets written down so the draw can be reversed
 * exactly rather than recalculated later against different stock.
 */
public final class FabricAllocator {

    /** One batch's share of a draw. */
    public record Share(FabricIntake intake, BigDecimal weight, int rolls) {}

    /**
     * The most one batch may give this draw.
     *
     * <p>For an ordinary run that is everything the batch has left. For a run cut
     * in one colour the weight narrows to what the batch holds of that colour;
     * the roll count does not, because the colour breakdown is a soft record that
     * need not add up to the batch, and capping rolls by it would invent a
     * shortage the shelf does not have.
     */
    public record Headroom(FabricIntake intake, BigDecimal weight, int rolls) {

        public static Headroom wholeBatch(FabricIntake intake) {
            return new Headroom(intake, intake.remainingQuantity(), intake.remainingRolls());
        }
    }

    private FabricAllocator() {
    }

    /** Every batch may give everything it has left. */
    public static List<Share> allocate(List<FabricIntake> batches, BigDecimal weight, int rolls) {
        return allocate(batches.stream().map(Headroom::wholeBatch).toList(), weight, rolls, "fabric");
    }

    /**
     * @param headroom oldest first; the caller decides the pool and the caps
     * @param weight   fabric to take off the shelf, waste included
     * @param rolls    rolls to take, not counting any that were already open
     * @param of       what came up short, for the message: "fabric", or a colour
     * @throws BusinessRuleException when the batches together cannot cover it
     */
    public static List<Share> allocate(
            List<Headroom> headroom, BigDecimal weight, int rolls, String of) {
        if (weight.signum() <= 0) {
            throw new BusinessRuleException("cut_summary_no_weight",
                    "Enter how much fabric this cut used");
        }

        List<Share> shares = new ArrayList<>();
        BigDecimal weightLeft = weight;
        int rollsLeft = rolls;

        for (Headroom available : headroom) {
            if (weightLeft.signum() <= 0 && rollsLeft <= 0) {
                break;
            }

            BigDecimal fromBatch = weightLeft.min(available.weight()).max(BigDecimal.ZERO);
            int rollsFromBatch = Math.max(0, Math.min(rollsLeft, available.rolls()));
            if (fromBatch.signum() <= 0 && rollsFromBatch == 0) {
                continue;
            }

            shares.add(new Share(available.intake(), fromBatch, rollsFromBatch));
            weightLeft = weightLeft.subtract(fromBatch);
            rollsLeft -= rollsFromBatch;
        }

        if (weightLeft.signum() > 0) {
            throw new BusinessRuleException("cut_summary_insufficient_fabric",
                    "The batches holding %s are %s short of the %s the cut used"
                            .formatted(of, weightLeft, weight));
        }
        if (rollsLeft > 0) {
            throw new BusinessRuleException("cut_summary_insufficient_rolls",
                    "The batches holding %s are %d rolls short of the %d the cut used"
                            .formatted(of, rollsLeft, rolls));
        }
        return shares;
    }

    /**
     * Splits the cut's waste across the shares in proportion to the weight each
     * one carries.
     *
     * <p>The عجز is a single figure for the whole cut — nobody weighed the bin
     * per batch — so the only defensible split is by how much of the cut each
     * batch supplied. The last share takes the rounding remainder, so the parts
     * add back to exactly the figure that was entered.
     *
     * @return waste per share, index for index
     */
    public static List<BigDecimal> splitWaste(List<Share> shares, BigDecimal waste) {
        return splitByWeight(shares.stream().map(Share::weight).toList(), waste);
    }

    /**
     * Splits {@code waste} across parts in proportion to their weights, the last
     * part taking the rounding remainder so the pieces add back to exactly the
     * figure entered. The same split a cut's عجز gets across its batches, and
     * across its colours before that.
     *
     * @return waste per weight, index for index
     */
    public static List<BigDecimal> splitByWeight(List<BigDecimal> weights, BigDecimal waste) {
        List<BigDecimal> split = new ArrayList<>();
        BigDecimal total = weights.stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        if (waste.signum() == 0 || total.signum() == 0) {
            weights.forEach(weight -> split.add(BigDecimal.ZERO));
            return split;
        }

        BigDecimal allocated = BigDecimal.ZERO;
        for (int index = 0; index < weights.size(); index++) {
            boolean last = index == weights.size() - 1;
            BigDecimal part = last
                    ? waste.subtract(allocated)
                    : waste.multiply(weights.get(index))
                            .divide(total, 3, java.math.RoundingMode.HALF_UP);
            split.add(part);
            allocated = allocated.add(part);
        }
        return split;
    }
}
