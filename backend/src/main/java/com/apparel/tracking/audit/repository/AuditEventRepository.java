package com.apparel.tracking.audit.repository;

import com.apparel.tracking.audit.domain.AuditEvent;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    Page<AuditEvent> findByEntityTypeAndEntityIdOrderByOccurredAtDesc(
            String entityType, Long entityId, Pageable pageable);

    Page<AuditEvent> findAllByOrderByOccurredAtDesc(Pageable pageable);
}
