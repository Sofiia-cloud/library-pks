package ru.mirea.library.dto;

import jakarta.validation.constraints.*;

import ru.mirea.library.model.RequestStatus;

import java.time.*;
import java.util.*;

public record StatusUpdate(@NotNull @PositiveOrZero Long version, @NotNull RequestStatus status) {}
