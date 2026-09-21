package com.apparel.tracking.production.domain;

import java.math.BigDecimal;

import com.apparel.tracking.common.model.BaseEntity;
import com.apparel.tracking.fabric.domain.FabricColor;
import com.apparel.tracking.fabric.domain.FabricIntake;

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
 * What a summary cut took from one batch.
 *
 * <p>Written when the cut is saved and never recalculated. The allocation is
 * oldest-batch-first, so it depends on what every batch held at that moment;
 * by the time the cut is edited or deleted, other cuts have drawn on those same
 * batches and the same calculation would land somewhere else. Replaying these
 * rows is what makes a reversal exact.
 */
@Entity
@Table(name = "cut_fabric_draw")
@Getter
@Setter
@NoArgsConstructor
public class CutFabricDraw extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cut_id", nullable = false)
    private Cut cut;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fabric_intake_id", nullable = false)
    private FabricIntake intake;

    /** Fabric from this batch that became garments. */
    @Column(name = "weight_consumed", nullable = false, precision = 14, scale = 3)
    private BigDecimal weightConsumed = BigDecimal.ZERO;

    /** Fabric from this batch binned with the rolls — the cut's عجز, its share. */
    @Column(name = "waste_weight", nullable = false, precision = 14, scale = 3)
    private BigDecimal wasteWeight = BigDecimal.ZERO;

    /**
     * The colour this share was for, on a run cut by colour. Null when the run
     * took the fabric as it came. Each colour's headroom on a batch is counted
     * from its own draws, so two colours off one purchase never borrow from each
     * other.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fabric_color_id")
    private FabricColor fabricColor;

    /** Rolls newly drawn off this batch, so its roll count can be put back. */
    @Column(name = "roll_count", nullable = false)
    private int rollCount;

    /** Everything that left the batch: cut and binned alike. */
    public BigDecimal weightOffTheBatch() {
        return weightConsumed.add(wasteWeight);
    }
}
