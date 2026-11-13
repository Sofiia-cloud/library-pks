package ru.mirea.library.dto;

import jakarta.validation.constraints.*;

import java.time.*;
import java.util.*;

public record ReaderActivityDto(UUID readerId, String fullName, long count) {}
