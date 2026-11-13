package ru.mirea.library.dto;

import jakarta.validation.constraints.*;

import java.time.*;
import java.util.*;

public record ReaderDto(
        UUID id,
        int readerNumber,
        String fullName,
        String email,
        String phone,
        String libraryCardNumber,
        LocalDateTime registeredAt,
        boolean active,
        LocalDateTime updatedAt,
        long version) {}
