package ru.mirea.library.dto;

import jakarta.validation.constraints.*;

import ru.mirea.library.model.*;

import java.time.*;
import java.util.*;

public record AnalyticsDto(
        long total,
        long active,
        long archived,
        long urgent,
        double averageCopies,
        List<NameCountDto> byReader,
        Map<RequestStatus, Long> byStatus,
        Map<ReturnState, Long> byReturnState) {}
