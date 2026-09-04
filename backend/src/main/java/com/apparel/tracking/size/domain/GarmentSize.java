package com.apparel.tracking.size.domain;

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

/** One size, e.g. 12 or XXL. Belongs to exactly one category. */
@Entity
@Table(name = "garment_size")
@Getter
@Setter
@NoArgsConstructor
public class GarmentSize extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "size_category_id", nullable = false)
    private SizeCategory category;

    @Column(name = "code", nullable = false, length = 32, unique = true)
    private String code;

    @Column(name = "name_ar", length = 64)
    private String nameAr;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
