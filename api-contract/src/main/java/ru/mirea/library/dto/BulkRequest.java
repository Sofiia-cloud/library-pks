package ru.mirea.library.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import ru.mirea.library.model.*;

import java.time.*;
import java.util.*;

public record BulkRequest(
        @NotEmpty @Size(max = 200) List<@Valid VersionedId> items,
        @NotNull BulkAction action,
        RequestStatus status,
        @Size(max = 60) String tag) {}
