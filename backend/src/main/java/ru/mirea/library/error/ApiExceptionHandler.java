package ru.mirea.library.error;

import jakarta.validation.ConstraintViolationException;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import ru.mirea.library.dto.ApiError;

import java.time.Instant;
import java.util.*;

@RestControllerAdvice
public class ApiExceptionHandler {
    private ResponseEntity<ApiError> error(
            int status, String code, String message, Map<String, String> fields) {
        return ResponseEntity.status(status)
                .body(new ApiError(Instant.now(), status, code, message, fields));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validation(MethodArgumentNotValidException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        e.getBindingResult()
                .getFieldErrors()
                .forEach(f -> fields.putIfAbsent(f.getField(), f.getDefaultMessage()));
        return error(400, "VALIDATION_ERROR", "Проверьте заполнение полей", fields);
    }

    @ExceptionHandler({
        HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class,
        MissingServletRequestParameterException.class,
        ConstraintViolationException.class,
        org.springframework.web.method.annotation.HandlerMethodValidationException.class
    })
    public ResponseEntity<ApiError> invalid(Exception e) {
        return error(400, "INVALID_REQUEST", "Некорректные параметры запроса", Map.of());
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> business(BusinessException e) {
        return error(400, "BUSINESS_RULE", e.getMessage(), Map.of());
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiError> missing(EntityNotFoundException e) {
        return error(404, "NOT_FOUND", e.getMessage(), Map.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> route(NoResourceFoundException e) {
        return error(404, "NOT_FOUND", "Ресурс не найден", Map.of());
    }

    @ExceptionHandler({
        ObjectOptimisticLockingFailureException.class,
        jakarta.persistence.OptimisticLockException.class
    })
    public ResponseEntity<ApiError> conflict(Exception e) {
        return error(
                409,
                "VERSION_CONFLICT",
                "Запись уже изменена. Загрузите актуальные данные",
                Map.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> integrity(Exception e) {
        return error(
                409,
                "DATA_CONFLICT",
                "Данные конфликтуют с существующей записью или связью",
                Map.of());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> unauthorized(AuthenticationException e) {
        return error(401, "UNAUTHORIZED", e.getMessage(), Map.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> forbidden(Exception e) {
        return error(403, "FORBIDDEN", "Недостаточно прав", Map.of());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> status(ResponseStatusException e) {
        return error(e.getStatusCode().value(), "REQUEST_REJECTED", e.getReason(), Map.of());
    }

    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> method(Exception e) {
        return error(405, "METHOD_NOT_ALLOWED", "Метод запроса не поддерживается", Map.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception e) {
        org.slf4j.LoggerFactory.getLogger(ApiExceptionHandler.class)
                .error("Unhandled API error", e);
        return error(500, "INTERNAL_ERROR", "Внутренняя ошибка сервера", Map.of());
    }
}
