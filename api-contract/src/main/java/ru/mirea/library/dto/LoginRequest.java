package ru.mirea.library.dto;

import jakarta.validation.constraints.*;

import java.time.*;
import java.util.*;

public record LoginRequest(
        @NotBlank @Size(max = 128) String login, @NotBlank @Size(max = 256) String password) {
    @Override
    public String toString() {
        return "LoginRequest[redacted]";
    }
}
