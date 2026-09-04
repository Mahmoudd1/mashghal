package com.apparel.tracking.pipeline.domain;

import java.time.Instant;
import java.time.LocalDate;

import com.apparel.tracking.production.domain.Model;
import com.apparel.tracking.reference.domain.Branch;
import com.apparel.tracking.reference.domain.PipelineStage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One recorded movement of pieces. Append-only.
 *
 * <p>A null {@code fromStage} seeds pieces into the pipeline (a cut allocated
 * them); a null {@code toStage} removes them (an allocation was reduced).
 */
@Entity
@Table(name = "stage_movement")
@Getter
@Setter
@NoArgsConstructor
public class StageMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "model_id", nullable = false)
    private Model model;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_stage_id")
    private PipelineStage fromStage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_stage_id")
    private PipelineStage toStage;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    /** The date the move happened on, which is not the row's write timestamp. */
    @Column(name = "movement_date", nullable = false)
    private LocalDate movementDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 64)
    private MovementReason reason;

    @Column(name = "note", length = 512)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "created_by", nullable = false, length = 64, updatable = false)
    private String createdBy;
}
