package ru.mirea.library.repository;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import ru.mirea.library.domain.UserAccount;

import java.util.*;

public interface UserAccountRepository
        extends JpaRepository<UserAccount, UUID>, JpaSpecificationExecutor<UserAccount> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "select u from UserAccount u where u.active=true and :role member of u.roles order by"
                    + " u.id")
    List<UserAccount> lockAdministrators(@Param("role") ru.mirea.library.model.Role role);

    Optional<UserAccount> findByLoginIgnoreCase(String login);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserAccount u where lower(u.login)=lower(:login)")
    Optional<UserAccount> lockByLogin(@Param("login") String login);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserAccount u where u.id=:id")
    Optional<UserAccount> lockById(@Param("id") UUID id);
}
