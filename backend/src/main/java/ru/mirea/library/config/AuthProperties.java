package ru.mirea.library.config;

import jakarta.validation.constraints.*;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
        @NotBlank String secretBase64,
        @NotBlank String issuer,
        @Min(1) @Max(60) int accessMinutes,
        @Min(1) @Max(90) int refreshDays) {}
