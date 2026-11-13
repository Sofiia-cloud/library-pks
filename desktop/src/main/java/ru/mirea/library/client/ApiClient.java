package ru.mirea.library.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import ru.mirea.library.dto.*;
import ru.mirea.library.model.Role;

import java.io.IOException;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

public class ApiClient {
    private final URI base;
    private final HttpClient http;
    private final ObjectMapper mapper =
            new ObjectMapper()
                    .registerModule(new JavaTimeModule())
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final Object refreshLock = new Object();

    private record SessionState(TokenResponse tokens, long epoch) {}

    private final java.util.concurrent.atomic.AtomicReference<SessionState> state =
            new java.util.concurrent.atomic.AtomicReference<>(new SessionState(null, 0));

    public ApiClient(String address) {
        base = URI.create(address.replaceAll("/+$", ""));
        if (!Set.of("http", "https").contains(base.getScheme())
                || base.getHost() == null
                || base.getUserInfo() != null
                || base.getQuery() != null
                || base.getFragment() != null)
            throw new IllegalArgumentException("Некорректный адрес API");
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public UserDto user() {
        TokenResponse tokens = state.get().tokens();
        return tokens == null ? null : tokens.user();
    }

    public boolean canEdit() {
        return hasRole(Role.ADMIN) || hasRole(Role.LIBRARIAN);
    }

    public boolean isAdmin() {
        return hasRole(Role.ADMIN);
    }

    public boolean hasRole(Role role) {
        UserDto user = user();
        return user != null && user.roles().contains(role);
    }

    public TokenResponse login(String login, String password) {
        synchronized (refreshLock) {
            TokenResponse result =
                    read(
                            send(
                                    "POST",
                                    "/api/auth/login",
                                    new LoginRequest(login, password),
                                    null),
                            TokenResponse.class);
            state.updateAndGet(old -> new SessionState(result, old.epoch() + 1));
            return result;
        }
    }

    public void logout() {
        synchronized (refreshLock) {
            SessionState old =
                    state.getAndUpdate(value -> new SessionState(null, value.epoch() + 1));
            if (old.tokens() != null)
                check(
                        send(
                                "POST",
                                "/api/auth/logout",
                                new RefreshRequest(old.tokens().refreshToken()),
                                null));
        }
    }

    public void clearSession() {
        state.updateAndGet(old -> new SessionState(null, old.epoch() + 1));
    }

    public void changePassword(String current, String replacement) {
        post(
                "/api/auth/change-password",
                new PasswordChangeRequest(current, replacement),
                Void.class);
        clearSession();
    }

    public UserDto me() {
        return get("/api/auth/me", UserDto.class);
    }

    public <T> T get(String path, Class<T> type) {
        return read(request("GET", path, null), type);
    }

    public <T> List<T> getList(String path, Class<T> type) {
        return read(
                request("GET", path, null),
                mapper.getTypeFactory().constructCollectionType(List.class, type));
    }

    public <T> T get(String path, TypeReference<T> type) {
        return read(request("GET", path, null), mapper.getTypeFactory().constructType(type));
    }

    public <T> T post(String path, Object body, Class<T> type) {
        return read(request("POST", path, body), type);
    }

    public <T> T put(String path, Object body, Class<T> type) {
        return read(request("PUT", path, body), type);
    }

    public String getText(String path) {
        var response = request("GET", path, null);
        check(response);
        return response.body();
    }

    public void delete(String path) {
        check(request("DELETE", path, null));
    }

    public static String query(Map<String, ?> values) {
        return values.entrySet().stream()
                .filter(e -> e.getValue() != null)
                .map(
                        e ->
                                URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)
                                        + "="
                                        + URLEncoder.encode(
                                                e.getValue().toString(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&", "?", ""));
    }

    private HttpResponse<String> request(String method, String path, Object body) {
        SessionState snapshot = state.get();
        if (snapshot.tokens() == null)
            throw new ApiException(401, "UNAUTHORIZED", "Войдите в систему");
        HttpResponse<String> response = send(method, path, body, snapshot.tokens().accessToken());
        if (response.statusCode() != 401) return response;
        synchronized (refreshLock) {
            SessionState current = state.get();
            if (current.epoch() != snapshot.epoch())
                throw new ApiException(
                        401, "SESSION_CHANGED", "Учётная запись изменилась. Повторите действие");
            if (current == snapshot) {
                try {
                    TokenResponse refreshed =
                            read(
                                    send(
                                            "POST",
                                            "/api/auth/refresh",
                                            new RefreshRequest(snapshot.tokens().refreshToken()),
                                            null),
                                    TokenResponse.class);
                    if (!state.compareAndSet(
                            snapshot, new SessionState(refreshed, snapshot.epoch())))
                        throw new ApiException(
                                401,
                                "SESSION_CHANGED",
                                "Учётная запись изменилась. Повторите действие");
                } catch (ApiException e) {
                    if (e.status() == 401 && !e.code().equals("SESSION_CHANGED"))
                        state.compareAndSet(snapshot, new SessionState(null, snapshot.epoch() + 1));
                    throw e;
                }
            }
            snapshot = state.get();
        }
        if (snapshot.tokens() == null)
            throw new ApiException(401, "UNAUTHORIZED", "Сессия истекла. Войдите снова");
        response = send(method, path, body, snapshot.tokens().accessToken());
        if (response.statusCode() == 401)
            state.compareAndSet(snapshot, new SessionState(null, snapshot.epoch() + 1));
        return response;
    }

    private HttpResponse<String> send(String method, String path, Object body, String access) {
        if (!path.startsWith("/api/")) throw new IllegalArgumentException("Ожидается путь API");
        try {
            HttpRequest.Builder builder =
                    HttpRequest.newBuilder(URI.create(base.toString() + path))
                            .timeout(Duration.ofSeconds(30))
                            .header(
                                    "Accept",
                                    path.startsWith("/api/book-requests/export")
                                            ? "text/csv, application/json"
                                            : "application/json");
            if (access != null) builder.header("Authorization", "Bearer " + access);
            HttpRequest.BodyPublisher publisher =
                    body == null
                            ? HttpRequest.BodyPublishers.noBody()
                            : HttpRequest.BodyPublishers.ofString(
                                    mapper.writeValueAsString(body), StandardCharsets.UTF_8);
            if (body != null) builder.header("Content-Type", "application/json");
            return http.send(
                    builder.method(method, publisher).build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(0, "INTERRUPTED", "Запрос отменён");
        } catch (IOException e) {
            throw new ApiException(
                    0,
                    "CONNECTION_ERROR",
                    "Не удалось связаться с сервером. Проверьте его запуск и адрес API");
        }
    }

    private void check(HttpResponse<String> response) {
        if (response.statusCode() >= 200 && response.statusCode() < 300) return;
        try {
            ApiError error = mapper.readValue(response.body(), ApiError.class);
            String message = error.message();
            if (error.fields() != null && !error.fields().isEmpty())
                message +=
                        ". "
                                + error.fields().entrySet().stream()
                                        .map(e -> e.getKey() + ": " + e.getValue())
                                        .collect(Collectors.joining("; "));
            throw new ApiException(response.statusCode(), error.code(), message);
        } catch (ApiException e) {
            throw e;
        } catch (IOException e) {
            throw new ApiException(
                    response.statusCode(),
                    "HTTP_ERROR",
                    "Ошибка сервера: HTTP " + response.statusCode());
        }
    }

    private <T> T read(HttpResponse<String> response, Class<T> type) {
        return read(response, mapper.getTypeFactory().constructType(type));
    }

    private <T> T read(HttpResponse<String> response, JavaType type) {
        check(response);
        if (type.hasRawClass(Void.class) || response.statusCode() == 204) return null;
        try {
            return mapper.readValue(response.body(), type);
        } catch (IOException e) {
            throw new ApiException(0, "INVALID_RESPONSE", "Сервер вернул некорректный ответ");
        }
    }
}
