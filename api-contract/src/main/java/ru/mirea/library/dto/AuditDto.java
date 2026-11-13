package ru.mirea.library.dto;

import jakarta.validation.constraints.*;

import ru.mirea.library.model.*;

import java.time.*;
import java.util.*;

public record AuditDto(
        UUID id,
        String actorLogin,
        String action,
        String entityType,
        UUID entityId,
        Instant occurredAt,
        String description,
        String correlationId) {}
