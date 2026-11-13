package ru.mirea.library.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "request_notes")
public class RequestNote {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;

    @Column(name = "request_id", nullable = false)
    public UUID requestId;

    @Column(name = "author_id")
    public UUID authorId;

    @Column(name = "author_login", nullable = false, length = 128)
    public String authorLogin;

    @Column(nullable = false, length = 4000)
    public String text;

    @Column(nullable = false)
    public boolean pinned;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    @Version public long version;

    @PrePersist
    void create() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void update() {
        updatedAt = Instant.now();
    }
}
