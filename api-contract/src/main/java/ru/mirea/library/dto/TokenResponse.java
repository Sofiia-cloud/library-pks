package ru.mirea.library.dto;

import jakarta.validation.constraints.*;

import java.time.*;
import java.util.*;

public record TokenResponse(
        String accessToken, String refreshToken, Instant expiresAt, UserDto user) {
    @Override
    public String toString() {
        return "TokenResponse[redacted]";
    }
}
