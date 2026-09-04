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

    /** Unusable leftover from cutting this roll — the "waste". */
    @Column(name = "defect_weight", nullable = false, precision = 14, scale = 3)
    private BigDecimal defectWeight = BigDecimal.ZERO;

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
     * the remainder is arithmetic. Finishing the roll means it used everything
     * left on it, so the figure is not asked for at all in that case.
     *
     * @param weightUsed ignored when {@code done} — a finished roll uses its whole balance
     */
    public void applyWeights(BigDecimal weightAtStart, BigDecimal weightUsed, boolean done) {
        BigDecimal used = done ? weightAtStart : weightUsed;

        if (used == null || used.signum() <= 0) {
            throw new BusinessRuleException("cut_roll_nothing_consumed",
                    "Enter how much of the roll this cut used");
        }
        if (used.compareTo(weightAtStart) > 0) {
            throw new BusinessRuleException("cut_roll_used_exceeds_roll",
                    "The roll held %s, so this cut cannot have used %s".formatted(weightAtStart, used));
        }

        this.weightAtStart = weightAtStart;
        this.weightConsumed = used;
        // Derived, never entered: the two can then never contradict each other.
        this.remainingAfter = weightAtStart.subtract(used);
        // Using every last gram finishes the roll whether or not the box was ticked.
        this.done = done || this.remainingAfter.signum() == 0;
    }
}
