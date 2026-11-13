package ru.mirea.library.dto;

import jakarta.validation.constraints.*;

import java.time.*;
import java.util.*;

public record ReaderUpdate(
        @NotNull @PositiveOrZero Long version,
        @NotBlank @Size(max = 150) String fullName,
        @NotBlank @Email @Size(max = 150) String email,
        @Size(max = 30) String phone,
        @NotBlank @Pattern(regexp = "LIB-[0-9]{4}") String libraryCardNumber) {}
