package com.apparel.tracking.production.domain;

import com.apparel.tracking.common.model.BaseEntity;
import com.apparel.tracking.reference.domain.Branch;
import com.apparel.tracking.size.domain.GarmentSize;

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
 * The marker: how many pieces of one size a single layer yields, for one model on
 * one cut.
 *
 * <p>Keyed on cut + model rather than cut + model + branch, because this is the
 * physical layout on the table. One layout serves whichever branches the pieces
 * are afterwards distributed to.
 */
@Entity
@Table(name = "cut_model_size")
@Getter
@Setter
@NoArgsConstructor
public class CutModelSize extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cut_id", nullable = false)
    private Cut cut;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "model_id", nullable = false)
    private Model model;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "garment_size_id", nullable = false)
    private GarmentSize size;

    @Column(name = "pieces_per_layer", nullable = false)
    private int piecesPerLayer;

    /**
     * Which branch sews this size. Null means the model's own sewing branch, so a
     * model that is not split needs no per-size branch at all.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Branch branch;
}
