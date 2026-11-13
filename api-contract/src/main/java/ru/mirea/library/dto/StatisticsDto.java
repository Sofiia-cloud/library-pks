package ru.mirea.library.dto;

import jakarta.validation.constraints.*;

import java.time.*;
import java.util.*;

public record StatisticsDto(
        long totalReaders,
        long totalRequests,
        long activeRequests,
        long returnedRequests,
        long cancelledOrRejected,
        long requestsLast30Days,
        double averageRequestsPerReader,
        Map<String, Long> statusCounts,
        List<NameCountDto> topBooks,
        List<NameCountDto> topAuthors) {}
