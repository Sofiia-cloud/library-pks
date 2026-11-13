package ru.mirea.library.dto;

import jakarta.validation.constraints.*;

import ru.mirea.library.model.*;

import java.time.*;
import java.util.*;

public record AdminUserUpdate(
        @NotNull @PositiveOrZero Long version,
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_.@-]{3,128}") String login,
        @NotBlank @Size(max = 150) String name,
        @NotEmpty Set<@NotNull Role> roles,
        boolean active) {}
