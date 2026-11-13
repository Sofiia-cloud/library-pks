package ru.mirea.library.dto;

import jakarta.validation.constraints.*;

import ru.mirea.library.model.*;

import java.time.*;
import java.util.*;

public record CsvPreview(
        UUID id, int validRows, int invalidRows, List<RowError> errors, Instant expiresAt) {
    public record RowError(int row, String message) {}
}
