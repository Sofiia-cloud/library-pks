package ru.mirea.library.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import ru.mirea.library.domain.RequestNote;

import java.util.*;

public interface NoteRepository extends JpaRepository<RequestNote, UUID> {
    List<RequestNote> findByRequestIdOrderByPinnedDescCreatedAtDesc(UUID requestId);
}
