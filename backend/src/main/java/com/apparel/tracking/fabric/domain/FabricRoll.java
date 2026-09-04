package com.apparel.tracking.fabric.domain;

import java.math.BigDecimal;

import com.apparel.tracking.common.exception.BusinessRuleException;
import com.apparel.tracking.common.model.BaseEntity;

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
 * One physical roll, created the first time it reaches a cutting table.
 *
 * <p>Intake states no per-roll weight, so a roll exists only once somebody has
 * weighed it to cut it. A roll that is not finished stays open with a balance,
 * keeping an identity a later cut can pick up.
 */
@Entity
@Table(name = "fabric_roll")
@Getter
@Setter
@NoArgsConstructor
public class FabricRoll extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fabric_intake_id", nullable = false)
    private FabricIntake intake;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fabric_color_id")
    private FabricColor color;

    @Column(name = "label", length = 64)
    private String label;

    @Column(name = "initial_weight", nullable = false, precision = 14, scale = 3)
    private BigDecimal initialWeight;

    @Column(name = "remaining_weight", nullable = false, precision = 14, scale = 3)
    private BigDecimal remainingWeight;

    /** True once a cut finished the roll. This is what decrements the batch count. */
    @Column(name = "closed", nullable = false)
    private boolean closed;

    public BigDecimal consumedWeight() {
        return initialWeight.subtract(remainingWeight);
    }

    public void requireOpen() {
        if (closed) {
            throw new BusinessRuleException("roll_closed",
                    "Roll %s is finished and cannot be added to another cut".formatted(displayName()));
        }
    }

    public String displayName() {
        return label != null ? label : "#" + getId();
    }
}
