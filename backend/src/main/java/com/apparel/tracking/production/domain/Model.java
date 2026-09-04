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
 * A garment model.
 *
 * <p>Deliberately has no planned-quantity or main-cut column: both are derived
 * from {@link CutModelAllocation} rows, because one model may be fed by several
 * cuts and one cut may feed several models.
 */
@Entity
@Table(name = "model")
@Getter
@Setter
@NoArgsConstructor
public class Model extends BaseEntity {

    @Column(name = "model_number", nullable = false, length = 64)
    private String modelNumber;

    @Column(name = "name_ar", nullable = false, length = 128)
    private String nameAr;

    @Column(name = "name_en", length = 128)
    private String nameEn;

    @Column(name = "note", length = 512)
    private String note;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    /**
     * Where this model is sewn by default. Individual sizes inherit it, and only
     * a model split across branches overrides it size by size.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sewing_branch_id")
    private Branch sewingBranch;
}
