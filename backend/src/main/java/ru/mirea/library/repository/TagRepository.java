package ru.mirea.library.repository;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import ru.mirea.library.domain.Tag;

public interface TagRepository extends JpaRepository<Tag, String> {
    @Modifying
    @Query(
            value = "INSERT INTO tags(name) VALUES (:name) ON CONFLICT DO NOTHING",
            nativeQuery = true)
    void insertIfMissing(@Param("name") String name);
}
