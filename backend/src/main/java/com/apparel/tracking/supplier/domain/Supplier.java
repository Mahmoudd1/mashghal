package com.apparel.tracking.supplier.domain;

import com.apparel.tracking.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Somebody fabric is bought from.
 *
 * <p>Not linked to a fabric type directly: which fabrics a supplier provides is
 * whatever their purchase history says, so one supplier can provide several
 * fabrics and one fabric can come from several suppliers without any extra
 * bookkeeping.
 */
@Entity
@Table(name = "supplier")
@Getter
@Setter
@NoArgsConstructor
public class Supplier extends BaseEntity {

    @Column(name = "name_ar", nullable = false, length = 128, unique = true)
    private String nameAr;

    @Column(name = "name_en", length = 128)
    private String nameEn;

    @Column(name = "phone", length = 64)
    private String phone;

    @Column(name = "note", length = 512)
    private String note;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
