package ru.mirea.library.dto;

import jakarta.validation.constraints.*;

import ru.mirea.library.model.*;

import java.time.*;
import java.util.*;

public record BulkResult(List<Item> results, int successful, int failed) {
    public record Item(UUID id, boolean success, int status, String message, Long version) {}
}
