package ru.mirea.library.dto;

import jakarta.validation.constraints.*;

import ru.mirea.library.model.RequestStatus;

import java.time.*;
import java.util.*;

public record BookRequestDto(
        UUID id,
        int requestNumber,
        UUID readerId,
        String readerName,
        String bookTitle,
        String bookAuthor,
        String isbn,
        RequestStatus status,
        LocalDateTime createdAt,
        LocalDate desiredReturnDate,
        String comment,
        int requestedCopies,
        boolean urgent,
        boolean archived,
        LocalDateTime updatedAt,
        long version) {}
