package com.apparel.tracking.fabric.domain;

import java.util.ArrayList;
import java.util.List;

import com.apparel.tracking.common.model.BaseEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "fabric_type")
@Getter
@Setter
@NoArgsConstructor
public class FabricType extends BaseEntity {

    @Column(name = "name_ar", nullable = false, length = 128)
    private String nameAr;

    @Column(name = "name_en", length = 128)
    private String nameEn;

    /** Fixed at creation; changing it once rolls exist would reinterpret their quantities. */
    @Enumerated(EnumType.STRING)
    @Column(name = "unit", nullable = false, length = 16)
    private FabricUnit unit;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @OneToMany(mappedBy = "fabricType", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FabricColor> colors = new ArrayList<>();

    public void addColor(FabricColor color) {
        color.setFabricType(this);
        colors.add(color);
    }
}
