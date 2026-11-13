package ru.mirea.library.dto;

import jakarta.validation.constraints.*;

import ru.mirea.library.model.*;

import java.time.*;
import java.util.*;

public record NoteDto(
        UUID id,
        UUID authorId,
        String authorLogin,
        String text,
        boolean pinned,
        Instant createdAt,
        Instant updatedAt,
        long version) {}
