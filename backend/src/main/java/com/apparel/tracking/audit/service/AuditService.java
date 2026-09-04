package com.apparel.tracking.audit.service;

import java.math.BigDecimal;

import com.apparel.tracking.audit.domain.AuditEvent;
import com.apparel.tracking.audit.repository.AuditEventRepository;
import com.apparel.tracking.config.JpaAuditingConfig;
import com.apparel.tracking.reference.domain.Branch;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the audit trail. Actions are recorded inside the caller's transaction
 * so a rolled-back change leaves no misleading trail entry.
 */
@Service
public class AuditService {

    /** Action codes, kept together so the log stays greppable. */
    public static final String FABRIC_ALLOCATED = "FABRIC_ALLOCATED";
    public static final String FABRIC_RELEASED = "FABRIC_RELEASED";
    public static final String MODEL_ALLOCATED = "MODEL_ALLOCATED";
    public static final String MODEL_ALLOCATION_CHANGED = "MODEL_ALLOCATION_CHANGED";
    public static final String MODEL_ALLOCATION_REMOVED = "MODEL_ALLOCATION_REMOVED";
    public static final String CUT_CLOSED = "CUT_CLOSED";
    public static final String CUT_REOPENED = "CUT_REOPENED";
    /** Recorded against a model's pipeline, never against a cut. */
    public static final String PIECES_FLAGGED = "PIECES_FLAGGED";

    private final AuditEventRepository events;

    public AuditService(AuditEventRepository events) {
        this.events = events;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String action, String entityType, Long entityId, Branch branch, BigDecimal quantity, String note) {
        AuditEvent event = new AuditEvent();
        event.setUsername(JpaAuditingConfig.currentUsername());
        event.setAction(action);
        event.setEntityType(entityType);
        event.setEntityId(entityId);
        event.setBranch(branch);
        event.setQuantity(quantity);
        event.setNote(note);
        events.save(event);
    }

}
