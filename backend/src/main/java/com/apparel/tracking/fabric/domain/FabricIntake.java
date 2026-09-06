package com.apparel.tracking.fabric.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.apparel.tracking.common.exception.BusinessRuleException;
import com.apparel.tracking.common.model.BaseEntity;
import com.apparel.tracking.supplier.domain.Supplier;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One purchase of fabric: a date, a roll count, a total weight or length, and the
 * price paid per unit that day.
 *
 * <p>This is the unit of stock. Consumption is tracked here as running counters,
 * so "how many rolls are left from the 26/08 batch" stays a subtraction no matter
 * how many cuts have drawn from it.
 *
 * <p>{@code derby} decides the pool: null for the fabric type's regular stock,
 * set when the batch tops up its derby.
 */
@Entity
@Table(name = "fabric_intake")
@Getter
@Setter
@NoArgsConstructor
public class FabricIntake extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fabric_type_id", nullable = false)
    private FabricType fabricType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "derby_id")
    private Derby derby;

    /** Who this batch came from. Null until known; never blocks recording. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @Column(name = "intake_date", nullable = false)
    private LocalDate intakeDate;

    @Column(name = "total_rolls", nullable = false)
    private int totalRolls;

    @Column(name = "total_quantity", nullable = false, precision = 14, scale = 3)
    private BigDecimal totalQuantity;

    /**
     * Per kg or per metre, matching the fabric type's unit. Null until the owner
     * records it — a purchase is a complete record without a price on it.
     */
    @Column(name = "price_per_unit", precision = 12, scale = 3)
    private BigDecimal pricePerUnit;

    @Column(name = "consumed_rolls", nullable = false)
    private int consumedRolls;

    @Column(name = "consumed_quantity", nullable = false, precision = 14, scale = 3)
    private BigDecimal consumedQuantity = BigDecimal.ZERO;

    /**
     * Fabric that left the batch without being cut: the leftovers thrown away
     * with rolls as they were finished.
     *
     * <p>Kept apart from {@code consumedQuantity} so "consumed" keeps meaning
     * fabric that went into garments. Both come off the same purchase, so it is
     * their sum that the batch total has to cover.
     */
    @Column(name = "wasted_quantity", nullable = false, precision = 14, scale = 3)
    private BigDecimal wastedQuantity = BigDecimal.ZERO;

    @Column(name = "note", length = 512)
    private String note;

    @OneToMany(mappedBy = "intake", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FabricIntakeColor> colorBreakdown = new ArrayList<>();

    public boolean isDerbyPool() {
        return derby != null;
    }

    public int remainingRolls() {
        return totalRolls - consumedRolls;
    }

    /** What is still on the shelf: neither cut nor thrown away. */
    public BigDecimal remainingQuantity() {
        return totalQuantity.subtract(consumedQuantity).subtract(wastedQuantity);
    }

    /**
     * True once every roll has come off the batch.
     *
     * <p>Derived from the counters rather than stored as a flag. A roll can be
     * released again when a cut is edited or deleted, and a stored flag would
     * have to be cleared in every one of those paths — miss one and a batch
     * stays "finished" while fabric is still on the floor. The subtraction
     * cannot drift.
     */
    public boolean isFinished() {
        return totalRolls > 0 && consumedRolls >= totalRolls;
    }

    /**
     * Fabric that was paid for but never went into a cut.
     *
     * <p>While the batch is live this is what has actually been recorded as
     * thrown away — the leftovers on rolls that were finished. The untouched
     * weight is not counted: it is still stock on the shelf, and calling it
     * waste would condemn a batch halfway through its life.
     *
     * <p>Once the last roll is gone there is no shelf left to sit on, so
     * anything the cuts never drew is waste too, recorded or not. That closes
     * the batch at exactly what was bought: consumed + wasted = total.
     */
    public BigDecimal wasteQuantity() {
        return isFinished()
                ? totalQuantity.subtract(consumedQuantity).max(BigDecimal.ZERO)
                : wastedQuantity;
    }

    /**
     * Waste as a share of what was bought, to two places.
     *
     * <p>Measured against the batch total rather than what was consumed, so it
     * answers the question the money asks: of the fabric we bought, how much did
     * we lose? While the batch is live it can only rise, as more rolls are
     * finished and more leftovers are recorded against it.
     */
    public BigDecimal wastePercentage() {
        if (totalQuantity.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return wasteQuantity()
                .multiply(BigDecimal.valueOf(100))
                .divide(totalQuantity, 2, RoundingMode.HALF_UP);
    }

    /** What this batch cost, or null while the price is still unknown. */
    public BigDecimal totalCost() {
        return pricePerUnit == null ? null : totalQuantity.multiply(pricePerUnit);
    }

    /** Rolls named in the colour breakdown; may be less than {@code totalRolls}. */
    public int assignedRolls() {
        return colorBreakdown.stream().mapToInt(FabricIntakeColor::getRollCount).sum();
    }

    /**
     * Draws weight off the batch. Called every time fabric comes off a roll,
     * whether or not that roll is finished.
     */
    public void consumeWeight(BigDecimal quantity) {
        if (quantity.signum() <= 0) {
            throw new BusinessRuleException("intake_allocation_not_positive",
                    "Consumed quantity must be greater than zero");
        }
        if (quantity.compareTo(remainingQuantity()) > 0) {
            throw new BusinessRuleException("intake_insufficient_quantity",
                    "The %s batch has %s left, cannot consume %s"
                            .formatted(intakeDate, remainingQuantity(), quantity));
        }
        consumedQuantity = consumedQuantity.add(quantity);
    }

    public void releaseWeight(BigDecimal quantity) {
        if (quantity.compareTo(consumedQuantity) > 0) {
            throw new BusinessRuleException("intake_release_exceeds_consumed",
                    "Releasing more weight than the %s batch has given out".formatted(intakeDate));
        }
        consumedQuantity = consumedQuantity.subtract(quantity);
    }

    /**
     * Throws weight away: the leftover on a roll that a cut has just finished.
     *
     * <p>Checked against what is left exactly as consumption is, because fabric
     * cannot be wasted twice any more than it can be cut twice.
     */
    public void wasteWeight(BigDecimal quantity) {
        if (quantity.signum() <= 0) {
            throw new BusinessRuleException("intake_waste_not_positive",
                    "Wasted quantity must be greater than zero");
        }
        if (quantity.compareTo(remainingQuantity()) > 0) {
            throw new BusinessRuleException("intake_insufficient_quantity",
                    "The %s batch has %s left, cannot waste %s"
                            .formatted(intakeDate, remainingQuantity(), quantity));
        }
        wastedQuantity = wastedQuantity.add(quantity);
    }

    public void releaseWaste(BigDecimal quantity) {
        if (quantity.compareTo(wastedQuantity) > 0) {
            throw new BusinessRuleException("intake_release_exceeds_consumed",
                    "Releasing more waste than the %s batch has recorded".formatted(intakeDate));
        }
        wastedQuantity = wastedQuantity.subtract(quantity);
    }

    /**
     * Marks one roll gone. Called exactly once per roll — at the moment a cut
     * finishes it, which may be long after the roll was first used.
     */
    public void consumeRoll() {
        if (consumedRolls >= totalRolls) {
            throw new BusinessRuleException("intake_insufficient_rolls",
                    "All %d rolls of the %s batch are already accounted for"
                            .formatted(totalRolls, intakeDate));
        }
        consumedRolls++;
    }

    public void releaseRoll() {
        if (consumedRolls <= 0) {
            throw new BusinessRuleException("intake_release_exceeds_consumed",
                    "No rolls of the %s batch are marked finished".formatted(intakeDate));
        }
        consumedRolls--;
    }
}
