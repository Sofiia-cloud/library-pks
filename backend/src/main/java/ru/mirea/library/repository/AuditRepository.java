package ru.mirea.library.repository;

import org.springframework.data.jpa.repository.*;

import ru.mirea.library.domain.AuditEvent;

import java.util.UUID;

public interface AuditRepository
        extends JpaRepository<AuditEvent, UUID>, JpaSpecificationExecutor<AuditEvent> {}
