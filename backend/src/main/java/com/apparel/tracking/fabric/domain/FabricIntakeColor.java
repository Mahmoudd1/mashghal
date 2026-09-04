package com.apparel.tracking.fabric.domain;

import java.math.BigDecimal;

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
 * "Of that batch, 50 rolls are blue."
 *
 * <p>A soft breakdown: the colour rows need not add up to the batch total, and a
 * partial breakdown saves normally. Weight per colour is optional.
 */
@Entity
@Table(name = "fabric_intake_color")
@Getter
@Setter
@NoArgsConstructor
public class FabricIntakeColor extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fabric_intake_id", nullable = false)
    private FabricIntake intake;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fabric_color_id", nullable = false)
    private FabricColor color;

    @Column(name = "roll_count", nullable = false)
    private int rollCount;

    /** Optional; the breakdown is valid without it. */
    @Column(name = "quantity", precision = 14, scale = 3)
    private BigDecimal quantity;
}
