package ru.mirea.library.dto;

import jakarta.validation.constraints.*;

import ru.mirea.library.model.*;

import java.time.*;
import java.util.*;

public record CsvPreviewRequest(
        @NotNull @Size(min = 1, max = 5000000) String content, @NotNull ImportMode mode) {
    @Override
    public String toString() {
        return "CsvPreviewRequest[content omitted]";
    }
}
