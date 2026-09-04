package com.apparel.tracking.audit.domain;

import java.math.BigDecimal;
import java.time.Instant;

import com.apparel.tracking.reference.domain.Branch;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Append-only record of something that happened: a stage transition, a roll
 * allocation, a flag. Never updated or deleted, so it does not extend
 * {@code BaseEntity} — there is no "who last changed this".
 */
@Entity
@Table(name = "audit_event")
@Getter
@Setter
@NoArgsConstructor
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();

    @Column(name = "username", nullable = false, length = 64)
    private String username;

    @Column(name = "action", nullable = false, length = 64)
    private String action;

    @Column(name = "entity_type", nullable = false, length = 64)
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    /** Pieces or fabric quantity involved, when the action moves an amount. */
    @Column(name = "quantity", precision = 12, scale = 3)
    private BigDecimal quantity;

    @Column(name = "note", length = 512)
    private String note;
}
