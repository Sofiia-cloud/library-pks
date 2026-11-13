package ru.mirea.library.dto;

import jakarta.validation.constraints.*;

import ru.mirea.library.model.RequestStatus;

import java.time.*;
import java.util.*;

public record BookRequestUpdate(
        @NotNull @PositiveOrZero Long version,
        @NotBlank @Size(max = 200) String bookTitle,
        @NotBlank @Size(max = 150) String bookAuthor,
        @Size(max = 20) String isbn,
        LocalDate desiredReturnDate,
        @Size(max = 4000) String comment,
        @NotNull RequestStatus status,
        @Min(1) @Max(1000) Integer requestedCopies,
        boolean urgent,
        @Size(max = 20) Set<@NotBlank @Size(max = 60) String> tags) {
    public BookRequestUpdate {
        requestedCopies = requestedCopies == null ? 1 : requestedCopies;
        tags = tags == null ? Set.of() : Set.copyOf(tags);
    }

    public BookRequestUpdate(
            Long version,
            String bookTitle,
            String bookAuthor,
            String isbn,
            LocalDate desiredReturnDate,
            String comment,
            RequestStatus status) {
        this(
                version,
                bookTitle,
                bookAuthor,
                isbn,
                desiredReturnDate,
                comment,
                status,
                1,
                false,
                Set.of());
    }
}
