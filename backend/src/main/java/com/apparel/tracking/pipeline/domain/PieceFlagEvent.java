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

/** Append-only record of pieces being marked defective, or that mark cleared. */
@Entity
@Table(name = "piece_flag_event")
@Getter
@Setter
@NoArgsConstructor
public class PieceFlagEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "model_id", nullable = false)
    private Model model;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stage_id", nullable = false)
    private PipelineStage stage;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 16)
    private FlagAction action;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "reason", length = 512)
    private String reason;

    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "created_by", nullable = false, length = 64, updatable = false)
    private String createdBy;
}
