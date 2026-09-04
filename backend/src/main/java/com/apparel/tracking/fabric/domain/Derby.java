package com.apparel.tracking.fabric.domain;

import com.apparel.tracking.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A fabric type's second stock pool.
 *
 * <p>At most one per fabric type, ever: a type either has a derby or it does not.
 * More derby fabric arriving is a new {@link FabricIntake} against this same
 * record, never a second derby. Only DERBY cuts may draw from it.
 */
@Entity
@Table(name = "derby")
@Getter
@Setter
@NoArgsConstructor
public class Derby extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fabric_type_id", nullable = false, unique = true)
    private FabricType fabricType;

    @Column(name = "note", length = 512)
    private String note;
}
