package ru.mirea.library.domain;

import jakarta.persistence.*;

import ru.mirea.library.model.Role;

import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "app_users")
public class UserAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 128)
    private String login;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword = true;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version private long version;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Set<Role> roles = new HashSet<>();

    protected UserAccount() {}

    public UserAccount(String login, String name, String passwordHash, Set<Role> roles) {
        this.login = login;
        this.name = name;
        this.passwordHash = passwordHash;
        this.roles = new HashSet<>(roles);
    }

    @PrePersist
    private void onCreate() {
        createdAt = Instant.now();
    }

    public void setLogin(String value) {
        login = value;
    }

    public void setName(String value) {
        name = value;
    }

    public void setRoles(Set<Role> value) {
        roles.clear();
        roles.addAll(value);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public long getVersion() {
        return version;
    }

    public UUID getId() {
        return id;
    }

    public String getLogin() {
        return login;
    }

    public String getName() {
        return name;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String value) {
        passwordHash = value;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean value) {
        active = value;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public void setMustChangePassword(boolean value) {
        mustChangePassword = value;
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    public void setFailedAttempts(int value) {
        failedAttempts = value;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public void setLockedUntil(Instant value) {
        lockedUntil = value;
    }

    public Set<Role> getRoles() {
        return Set.copyOf(roles);
    }
}
