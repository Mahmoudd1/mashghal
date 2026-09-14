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
 * <p>Pure arithmetic: nothing here touches a batch. The caller applies the
 * result, which is also what gets written down so the draw can be reversed
 * exactly rather than recalculated later against different stock.
 */
public final class FabricAllocator {

    /** One batch's share of a draw. */
    public record Share(FabricIntake intake, BigDecimal weight, int rolls) {}

    private FabricAllocator() {
    }

    /**
     * @param batches oldest first; the caller decides which pool they come from
     * @param weight  fabric to take off the shelf, waste included
     * @param rolls   rolls to take, not counting any that were already open
     * @throws BusinessRuleException when the batches together cannot cover it
     */
    public static List<Share> allocate(List<FabricIntake> batches, BigDecimal weight, int rolls) {
        if (weight.signum() <= 0) {
            throw new BusinessRuleException("cut_summary_no_weight",
                    "Enter how much fabric this cut used");
        }

        List<Share> shares = new ArrayList<>();
        BigDecimal weightLeft = weight;
        int rollsLeft = rolls;

        for (FabricIntake batch : batches) {
            if (weightLeft.signum() <= 0 && rollsLeft <= 0) {
                break;
            }

            BigDecimal fromBatch = weightLeft.min(batch.remainingQuantity()).max(BigDecimal.ZERO);
            int rollsFromBatch = Math.max(0, Math.min(rollsLeft, batch.remainingRolls()));
            if (fromBatch.signum() <= 0 && rollsFromBatch == 0) {
                continue;
            }

            shares.add(new Share(batch, fromBatch, rollsFromBatch));
            weightLeft = weightLeft.subtract(fromBatch);
            rollsLeft -= rollsFromBatch;
        }

        if (weightLeft.signum() > 0) {
            throw new BusinessRuleException("cut_summary_insufficient_fabric",
                    "This fabric's batches are %s short of the %s the cut used"
                            .formatted(weightLeft, weight));
        }
        if (rollsLeft > 0) {
            throw new BusinessRuleException("cut_summary_insufficient_rolls",
                    "This fabric's batches are %d rolls short of the %d the cut used"
                            .formatted(rollsLeft, rolls));
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
        List<BigDecimal> split = new ArrayList<>();
        BigDecimal total = shares.stream()
                .map(Share::weight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (waste.signum() == 0 || total.signum() == 0) {
            shares.forEach(share -> split.add(BigDecimal.ZERO));
            return split;
        }

        BigDecimal allocated = BigDecimal.ZERO;
        for (int index = 0; index < shares.size(); index++) {
            boolean last = index == shares.size() - 1;
            BigDecimal part = last
                    ? waste.subtract(allocated)
                    : waste.multiply(shares.get(index).weight())
                            .divide(total, 3, java.math.RoundingMode.HALF_UP);
            split.add(part);
            allocated = allocated.add(part);
        }
        return split;
    }
}
