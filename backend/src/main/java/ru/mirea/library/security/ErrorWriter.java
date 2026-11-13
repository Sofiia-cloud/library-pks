package ru.mirea.library.security;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;

import ru.mirea.library.dto.ApiError;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

@Component
public class ErrorWriter {
    private final ObjectMapper mapper;

    public ErrorWriter(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public void write(HttpServletResponse response, int status, String code, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(
                response.getOutputStream(),
                new ApiError(Instant.now(), status, code, message, Map.of()));
    }
}
