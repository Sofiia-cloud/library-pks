package ru.mirea.library.dto;

import jakarta.validation.constraints.*;

import ru.mirea.library.model.*;

import java.time.*;
import java.util.*;

public record PasswordReset(
        @NotNull @PositiveOrZero Long version,
        @NotBlank @Size(min = 10, max = 72) String temporaryPassword) {
    @Override
    public String toString() {
        return "PasswordReset[redacted]";
    }
}
