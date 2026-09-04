package com.apparel.tracking.fabric.domain;

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

/** A colour is scoped to one fabric type — the same colour name may exist under several types. */
@Entity
@Table(name = "fabric_color")
@Getter
@Setter
@NoArgsConstructor
public class FabricColor extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fabric_type_id", nullable = false)
    private FabricType fabricType;

    @Column(name = "name_ar", nullable = false, length = 128)
    private String nameAr;

    @Column(name = "name_en", length = 128)
    private String nameEn;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
