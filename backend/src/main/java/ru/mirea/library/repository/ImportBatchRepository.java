package ru.mirea.library.repository;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import ru.mirea.library.domain.ImportBatch;

import java.util.*;

public interface ImportBatchRepository extends JpaRepository<ImportBatch, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from ImportBatch b where b.id=:id")
    Optional<ImportBatch> lockById(@Param("id") UUID id);

    @Modifying
    @Query("delete from ImportBatch b where b.expiresAt<:time")
    void deleteExpired(@Param("time") java.time.Instant time);
}
