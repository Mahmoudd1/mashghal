package com.apparel.tracking.production.domain;

import com.apparel.tracking.common.model.BaseEntity;
import com.apparel.tracking.reference.domain.Branch;

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
 * "This cut contributed N pieces to this model, for production at this branch."
 *
 * <p>The source of truth for planned quantity. A model's plan for a branch is
 * the sum of its allocation rows there — across however many cuts feed it.
 */
@Entity
@Table(name = "cut_model_allocation")
@Getter
@Setter
@NoArgsConstructor
public class CutModelAllocation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cut_id", nullable = false)
    private Cut cut;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "model_id", nullable = false)
    private Model model;

    /** Where these pieces are produced — not necessarily where they were cut. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @Column(name = "quantity_allocated", nullable = false)
    private int quantityAllocated;

    @Column(name = "note", length = 512)
    private String note;
}
