package com.apparel.tracking.reference.domain;

import com.apparel.tracking.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One step of the production pipeline. Seeded with CUTTING, SEWING, RECEIVED
 * and SOLD; {@code sequenceNo} defines the order so extra stages can be
 * inserted later (leave gaps between seeded sequence numbers).
 */
@Entity
@Table(name = "pipeline_stage")
@Getter
@Setter
@NoArgsConstructor
public class PipelineStage extends BaseEntity {

    @Column(name = "code", nullable = false, length = 32, unique = true)
    private String code;

    @Column(name = "name_ar", nullable = false, length = 128)
    private String nameAr;

    @Column(name = "name_en", length = 128)
    private String nameEn;

    /** Ordering within the pipeline. Seeded as 100, 200, 300, 400. */
    @Column(name = "sequence_no", nullable = false, unique = true)
    private int sequenceNo;

    /** True for the last stage; nothing moves out of a terminal stage. */
    @Column(name = "terminal", nullable = false)
    private boolean terminal;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
