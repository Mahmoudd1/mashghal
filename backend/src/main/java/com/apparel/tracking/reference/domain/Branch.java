package com.apparel.tracking.reference.domain;

import com.apparel.tracking.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A manufacturing site. Seeded with Agamy and Smouha, but stored as a lookup
 * row so further branches can be added without a schema change.
 */
@Entity
@Table(name = "branch")
@Getter
@Setter
@NoArgsConstructor
public class Branch extends BaseEntity {

    @Column(name = "code", nullable = false, length = 32, unique = true)
    private String code;

    @Column(name = "name_ar", nullable = false, length = 128)
    private String nameAr;

    /** Optional English label, reserved for a future bilingual data-entry UI. */
    @Column(name = "name_en", length = 128)
    private String nameEn;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
