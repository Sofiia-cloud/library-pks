package ru.mirea.library.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_sessions")
public class RefreshSession {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean revoked;

    @Version private long version;

    protected RefreshSession() {}

    public RefreshSession(UserAccount user, String hash, Instant now, Instant expiresAt) {
        this.user = user;
        this.tokenHash = hash;
        this.createdAt = now;
        this.expiresAt = expiresAt;
    }

    public UUID getId() {
        return id;
    }

    public UserAccount getUser() {
        return user;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String hash) {
        tokenHash = hash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isRevoked() {
        return revoked;
    }

    public void setRevoked(boolean value) {
        revoked = value;
    }
}
