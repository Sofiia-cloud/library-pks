package ru.mirea.library.dto;

import jakarta.validation.constraints.*;

import java.time.*;
import java.util.*;

public record RefreshRequest(@NotBlank @Size(min = 32, max = 256) String refreshToken) {
    @Override
    public String toString() {
        return "RefreshRequest[redacted]";
    }
}
