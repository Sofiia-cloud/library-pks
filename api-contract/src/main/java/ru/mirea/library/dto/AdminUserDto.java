package ru.mirea.library.dto;

import jakarta.validation.constraints.*;

import ru.mirea.library.model.*;

import java.time.*;
import java.util.*;

public record AdminUserDto(
        UUID id,
        String login,
        String name,
        Set<Role> roles,
        boolean active,
        boolean mustChangePassword,
        Instant createdAt,
        long version) {}
