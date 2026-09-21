package com.apparel.tracking.production.domain;

import java.math.BigDecimal;

import com.apparel.tracking.common.model.BaseEntity;
import com.apparel.tracking.fabric.domain.FabricColor;

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
 * One colour of a run cut by colour: "5 kg of navy, off 2 rolls".
 *
 * <p>Each line is drawn down its own colour's batches, oldest first, so a run
 * that laid out navy and black takes each from the purchases that hold it. The
 * cut's totals are the sum of its lines.
 */
@Entity
@Table(name = "cut_color_line")
@Getter
@Setter
@NoArgsConstructor
public class CutColorLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cut_id", nullable = false)
    private Cut cut;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fabric_color_id", nullable = false)
    private FabricColor color;

    /** Fabric of this colour taken off the shelf, its share of the عجز included. */
    @Column(name = "weight", nullable = false, precision = 14, scale = 3)
    private BigDecimal weight;

    @Column(name = "total_rolls", nullable = false)
    private int totalRolls;

    /** Of {@link #totalRolls}, the ones an earlier run had already opened. */
    @Column(name = "reused_rolls", nullable = false)
    private int reusedRolls;

    /** Rolls this line takes off a batch: the ones nobody had opened yet. */
    public int newRolls() {
        return totalRolls - reusedRolls;
    }
}
