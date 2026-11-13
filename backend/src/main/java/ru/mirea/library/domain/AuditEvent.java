package ru.mirea.library.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_events")
public class AuditEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "actor_login", nullable = false, length = 128)
    private String actorLogin;

    @Column(nullable = false, length = 40)
    private String action;

    @Column(name = "entity_type", nullable = false, length = 40)
    private String entityType;

    @Column(name = "entity_id")
    private UUID entityId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(name = "correlation_id", nullable = false, length = 64)
    private String correlationId;

    protected AuditEvent() {}

    public AuditEvent(
            String actor,
            String action,
            String type,
            UUID target,
            String description,
            String correlation) {
        this.actorLogin = actor;
        this.action = action;
        this.entityType = type;
        this.entityId = target;
        this.description = description;
        this.correlationId = correlation;
        this.occurredAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getActorLogin() {
        return actorLogin;
    }

    public String getAction() {
        return action;
    }

    public String getEntityType() {
        return entityType;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getDescription() {
        return description;
    }

    public String getCorrelationId() {
        return correlationId;
    }
}
