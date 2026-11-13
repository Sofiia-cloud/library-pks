package ru.mirea.library.dto;

import jakarta.validation.constraints.*;

import java.time.*;
import java.util.*;

public record PasswordChangeRequest(
        @NotBlank @Size(max = 256) String currentPassword,
        @NotBlank @Size(min = 10, max = 72) String newPassword) {
    @Override
    public String toString() {
        return "PasswordChangeRequest[redacted]";
    }
}
