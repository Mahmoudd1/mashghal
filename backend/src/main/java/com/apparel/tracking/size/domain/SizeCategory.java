package com.apparel.tracking.size.domain;

import java.util.ArrayList;
import java.util.List;

import com.apparel.tracking.common.model.BaseEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A band of sizes — اولادي, مقاسات محيرة, رجالي, مقاسات خاصة.
 *
 * <p>Exists so reporting can ask about a whole range without naming every size.
 * Editable, but expected to change rarely.
 */
@Entity
@Table(name = "size_category")
@Getter
@Setter
@NoArgsConstructor
public class SizeCategory extends BaseEntity {

    @Column(name = "code", nullable = false, length = 32, unique = true)
    private String code;

    @Column(name = "name_ar", nullable = false, length = 128)
    private String nameAr;

    @Column(name = "name_en", length = 128)
    private String nameEn;

    @Column(name = "note", length = 512)
    private String note;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @OneToMany(mappedBy = "category", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder asc")
    private List<GarmentSize> sizes = new ArrayList<>();
}
