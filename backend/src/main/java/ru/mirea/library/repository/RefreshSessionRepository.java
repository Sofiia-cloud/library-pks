package ru.mirea.library.repository;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import ru.mirea.library.domain.RefreshSession;

import java.util.*;

public interface RefreshSessionRepository extends JpaRepository<RefreshSession, UUID> {
    @Override
    @EntityGraph(attributePaths = {"user", "user.roles"})
    Optional<RefreshSession> findById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from RefreshSession s where s.tokenHash=:hash")
    Optional<RefreshSession> lockByTokenHash(@Param("hash") String hash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            "update RefreshSession s set s.revoked=true, s.version=s.version+1 where"
                + " s.user.id=:userId and s.revoked=false")
    int revokeForUser(@Param("userId") UUID userId);
}
