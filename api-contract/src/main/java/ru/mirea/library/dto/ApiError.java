package ru.mirea.library.dto;

import jakarta.validation.constraints.*;

import java.time.*;
import java.util.*;

public record ApiError(
        Instant timestamp, int status, String code, String message, Map<String, String> fields) {}
