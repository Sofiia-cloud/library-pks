package ru.mirea.library.dto;

import jakarta.validation.constraints.*;

import ru.mirea.library.model.Role;

import java.time.*;
import java.util.*;

public record UserDto(
        UUID id, String login, String name, Set<Role> roles, boolean mustChangePassword) {
    public UserDto {
        roles = Set.copyOf(roles);
    }
}
