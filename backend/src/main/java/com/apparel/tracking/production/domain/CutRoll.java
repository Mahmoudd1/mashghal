package com.apparel.tracking.production.domain;

import java.math.BigDecimal;

import com.apparel.tracking.common.exception.BusinessRuleException;
import com.apparel.tracking.common.model.BaseEntity;
import com.apparel.tracking.fabric.domain.FabricRoll;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One roll's use by one cutting run.
 *
 * <p>{@code weightAtStart} is what the roll held when this cut picked it up, so a
 * roll used across several cuts records each leg honestly rather than repeating
 * its original weight. {@code weightConsumed} is always the difference between
 * that and what was left, which is what stops the weight and count dimensions
 * drifting apart.
 */
@Entity
@Table(name = "cut_roll")
@Getter
@Setter
@NoArgsConstructor
public class CutRoll extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cut_id", nullable = false)
    private Cut cut;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fabric_roll_id", nullable = false)
    private FabricRoll fabricRoll;

    /** How many layers this roll was cut into on this run. */
    @Column(name = "layers", nullable = false)
    private int layers;

    /** Unusable fabric from within what was cut — the cutting defect. */
    @Column(name = "defect_weight", nullable = false, precision = 14, scale = 3)
    private BigDecimal defectWeight = BigDecimal.ZERO;

    /**
     * Fabric still on the roll when this run finished it, and thrown away with it.
     *
     * <p>Distinct from {@link #defectWeight}, which is spoilage inside what the
     * cut actually used. This never reached the table at all.
     */
    @Column(name = "waste_weight", nullable = false, precision = 14, scale = 3)
    private BigDecimal wasteWeight = BigDecimal.ZERO;

    @Column(name = "weight_at_start", nullable = false, precision = 14, scale = 3)
    private BigDecimal weightAtStart;

    @Column(name = "remaining_after", nullable = false, precision = 14, scale = 3)
    private BigDecimal remainingAfter = BigDecimal.ZERO;

    @Column(name = "weight_consumed", nullable = false, precision = 14, scale = 3)
    private BigDecimal weightConsumed;

    /** True when this cut finished the roll; defaults to true. */
    @Column(name = "done", nullable = false)
    private boolean done = true;

    @Column(name = "note", length = 512)
    private String note;

    /**
     * Sets the weights from what the roll held and how much this cut used.
     *
     * <p>Used weight is the input because that is what gets weighed at the table;
     * everything else is arithmetic. What happens to the balance the cut did not
     * use is the whole question, and it depends on whether the roll is finished:
     *
     * <ul>
     *   <li><b>Open</b> — the balance stays on the roll as {@code remainingAfter},
     *       waiting for the next cut.</li>
     *   <li><b>Finished</b> — the roll is gone, so the balance goes with it and is
     *       recorded as {@code wasteWeight}. Finishing a roll does not mean the cut
     *       used every gram of it.</li>
     * </ul>
     *
     * @param weightUsed what this cut took off the roll. Omitting it while finishing
     *                   the roll means the cut used the whole balance, wasting none.
     */
    public void applyWeights(BigDecimal weightAtStart, BigDecimal weightUsed, boolean done) {
        // A finished roll with no figure given used all of it — the old behaviour,
        // and still the right reading of "done" when nobody weighed a leftover.
        BigDecimal used = weightUsed != null ? weightUsed : (done ? weightAtStart : null);

        if (used == null || used.signum() <= 0) {
            throw new BusinessRuleException("cut_roll_nothing_consumed",
                    "Enter how much of the roll this cut used");
        }
        if (used.compareTo(weightAtStart) > 0) {
            throw new BusinessRuleException("cut_roll_used_exceeds_roll",
                    "The roll held %s, so this cut cannot have used %s".formatted(weightAtStart, used));
        }

        BigDecimal leftover = weightAtStart.subtract(used);

        this.weightAtStart = weightAtStart;
        this.weightConsumed = used;
        // Using every last gram finishes the roll whether or not the box was ticked.
        this.done = done || leftover.signum() == 0;
        // Derived, never entered: the three can then never contradict each other.
        this.wasteWeight = this.done ? leftover : BigDecimal.ZERO;
        this.remainingAfter = this.done ? BigDecimal.ZERO : leftover;
    }

    /** Everything this run took off the roll, cut or thrown away. */
    public BigDecimal weightOffTheRoll() {
        return weightConsumed.add(wasteWeight);
    }
}
