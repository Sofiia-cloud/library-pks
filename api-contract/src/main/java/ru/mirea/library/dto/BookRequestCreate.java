package ru.mirea.library.dto;

import jakarta.validation.constraints.*;

import ru.mirea.library.model.RequestStatus;

import java.time.*;
import java.util.*;

public record BookRequestCreate(
        @NotNull UUID readerId,
        @NotBlank @Size(max = 200) String bookTitle,
        @NotBlank @Size(max = 150) String bookAuthor,
        @Size(max = 20) String isbn,
        LocalDate desiredReturnDate,
        @Size(max = 4000) String comment,
        @NotNull RequestStatus status,
        @Min(1) @Max(1000) Integer requestedCopies,
        boolean urgent,
        @Size(max = 20) Set<@NotBlank @Size(max = 60) String> tags,
        @Pattern(regexp = "[A-Za-z0-9._-]{1,64}") String externalCode) {
    public BookRequestCreate {
        requestedCopies = requestedCopies == null ? 1 : requestedCopies;
        tags = tags == null ? Set.of() : Set.copyOf(tags);
    }

    public BookRequestCreate(
            UUID readerId,
            String bookTitle,
            String bookAuthor,
            String isbn,
            LocalDate desiredReturnDate,
            String comment,
            RequestStatus status) {
        this(
                readerId,
                bookTitle,
                bookAuthor,
                isbn,
                desiredReturnDate,
                comment,
                status,
                1,
                false,
                Set.of(),
                null);
    }
}
