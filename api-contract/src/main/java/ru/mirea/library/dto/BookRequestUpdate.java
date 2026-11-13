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
        String comment,
        @NotNull RequestStatus status) {}
