package ru.mirea.library.domain;

import jakarta.persistence.*;

import ru.mirea.library.model.ImportMode;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "import_batches")
public class ImportBatch {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;

    @Column(name = "owner_id", nullable = false)
    public UUID ownerId;

    @Column(nullable = false, columnDefinition = "text")
    public String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public ImportMode mode;

    @Column(nullable = false, columnDefinition = "text")
    public String versions;

    @Column(name = "expires_at", nullable = false)
    public Instant expiresAt;

    @Column(name = "valid_rows", nullable = false)
    public int validRows;

    @Column(name = "invalid_rows", nullable = false)
    public int invalidRows;

    @Column(nullable = false)
    public boolean applied;

    @Column(name = "created_count", nullable = false)
    public int createdCount;

    @Column(name = "updated_count", nullable = false)
    public int updatedCount;
}
