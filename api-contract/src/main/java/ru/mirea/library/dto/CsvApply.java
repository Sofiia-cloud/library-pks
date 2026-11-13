package ru.mirea.library.dto;

import jakarta.validation.constraints.*;

import ru.mirea.library.model.*;

import java.time.*;
import java.util.*;

public record CsvApply(@NotNull UUID previewId) {}
