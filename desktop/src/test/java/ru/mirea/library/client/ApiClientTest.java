package ru.mirea.library.client;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sun.net.httpserver.*;

import org.junit.jupiter.api.*;

import ru.mirea.library.dto.*;
import ru.mirea.library.model.Role;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

class ApiClientTest {
    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());
    private HttpServer server;
    private ExecutorService workers;
    private ApiClient api;
    private volatile BiConsumer<HttpExchange, String> responder;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        workers = Executors.newCachedThreadPool();
        server.setExecutor(workers);
        server.createContext(
                "/api/",
                exchange -> {
                    String body =
                            new String(
                                    exchange.getRequestBody().readAllBytes(),
                                    StandardCharsets.UTF_8);
                    responder.accept(exchange, body);
                });
        responder =
                (exchange, body) ->
                        reply(
                                exchange,
                                404,
                                new ApiError(
                                        Instant.now(), 404, "NOT_FOUND", "Не найдено", Map.of()));
        server.start();
        api = new ApiClient("http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void stop() {
        server.stop(0);
        workers.shutdownNow();
    }

    @Test
    void serializesLoginAndKeepsSessionOnlyInMemory() {
        responder =
                (exchange, body) -> {
                    try {
                        JsonNode sent = json.readTree(body);
                        assertEquals("admin", sent.get("login").asText());
                        assertEquals("password-value", sent.get("password").asText());
                        reply(exchange, 200, tokens("old"));
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                };
        TokenResponse result = api.login("admin", "password-value");
        assertEquals(userId, api.user().id());
        assertTrue(api.isAdmin());
        assertTrue(api.canEdit());
        assertFalse(result.toString().contains("refresh-old"));
    }

    @Test
    void refreshesOnlyOnceForConcurrentUnauthorizedRequests() throws Exception {
        AtomicInteger refreshes = new AtomicInteger();
        CountDownLatch both = new CountDownLatch(2);
        responder =
                (exchange, body) -> {
                    String path = exchange.getRequestURI().getPath();
                    if (path.endsWith("/login")) {
                        reply(exchange, 200, tokens("old"));
                        return;
                    }
                    if (path.endsWith("/refresh")) {
                        refreshes.incrementAndGet();
                        reply(exchange, 200, tokens("new"));
                        return;
                    }
                    if ("Bearer access-old"
                            .equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
                        both.countDown();
                        try {
                            assertTrue(both.await(5, TimeUnit.SECONDS));
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        reply(
                                exchange,
                                401,
                                new ApiError(
                                        Instant.now(), 401, "UNAUTHORIZED", "Истёк", Map.of()));
                        return;
                    }
                    reply(exchange, 200, tokens("new").user());
                };
        api.login("admin", "password-value");
        ExecutorService calls = Executors.newFixedThreadPool(2);
        try {
            Future<UserDto> one = calls.submit(api::me), two = calls.submit(api::me);
            assertEquals(userId, one.get(10, TimeUnit.SECONDS).id());
            assertEquals(userId, two.get(10, TimeUnit.SECONDS).id());
            assertEquals(1, refreshes.get());
        } finally {
            calls.shutdownNow();
        }
    }

    @Test
    void preservesConflictErrorsWithoutRetryingMutation() {
        AtomicInteger writes = new AtomicInteger(), refreshes = new AtomicInteger();
        responder =
                (exchange, body) -> {
                    if (exchange.getRequestURI().getPath().endsWith("/login")) {
                        reply(exchange, 200, tokens("old"));
                        return;
                    }
                    if (exchange.getRequestURI().getPath().endsWith("/refresh"))
                        refreshes.incrementAndGet();
                    writes.incrementAndGet();
                    reply(
                            exchange,
                            409,
                            new ApiError(
                                    Instant.now(),
                                    409,
                                    "VERSION_CONFLICT",
                                    "Запись изменена",
                                    Map.of()));
                };
        api.login("admin", "password-value");
        ApiException error =
                assertThrows(
                        ApiException.class,
                        () ->
                                api.put(
                                        "/api/book-requests/" + UUID.randomUUID(),
                                        Map.of("version", 0),
                                        BookRequestDto.class));
        assertEquals(409, error.status());
        assertEquals("VERSION_CONFLICT", error.code());
        assertEquals(1, writes.get());
        assertEquals(0, refreshes.get());
    }

    @Test
    void clearsSessionAfterPasswordChange() {
        responder =
                (exchange, body) -> {
                    if (exchange.getRequestURI().getPath().endsWith("/login"))
                        reply(exchange, 200, tokens("old"));
                    else reply(exchange, 204, null);
                };
        api.login("admin", "password-value");
        api.changePassword("password-value", "new-password-value");
        assertNull(api.user());
        assertEquals(401, assertThrows(ApiException.class, api::me).status());
    }

    @Test
    void clearsSessionWhenRefreshSessionIsRevoked() {
        responder =
                (exchange, body) -> {
                    if (exchange.getRequestURI().getPath().endsWith("/login"))
                        reply(exchange, 200, tokens("old"));
                    else
                        reply(
                                exchange,
                                401,
                                new ApiError(
                                        Instant.now(),
                                        401,
                                        "UNAUTHORIZED",
                                        "Сессия отозвана",
                                        Map.of()));
                };
        api.login("admin", "password-value");
        assertEquals(401, assertThrows(ApiException.class, api::me).status());
        assertNull(api.user());
    }

    private TokenResponse tokens(String suffix) {
        return new TokenResponse(
                "access-" + suffix,
                "refresh-" + suffix,
                Instant.now().plusSeconds(600),
                new UserDto(userId, "admin", "Администратор", Set.of(Role.ADMIN), false));
    }

    private void reply(HttpExchange exchange, int status, Object value) {
        try {
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            if (status == 204) {
                exchange.sendResponseHeaders(status, -1);
                return;
            }
            byte[] bytes = json.writeValueAsBytes(value);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            exchange.close();
        }
    }
}
