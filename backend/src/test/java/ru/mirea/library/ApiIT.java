package ru.mirea.library;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.*;

import org.junit.jupiter.api.*;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import ru.mirea.library.domain.UserAccount;
import ru.mirea.library.dto.*;
import ru.mirea.library.model.*;
import ru.mirea.library.repository.*;

import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApiIT {
    private static final String PASSWORD = "TestPassword123!";
    private ConfigurableApplicationContext context;
    private HttpClient http;
    private ObjectMapper json;
    private String base, schema;
    private PGSimpleDataSource source;
    private UserAccountRepository users;
    private RefreshSessionRepository sessions;
    private String passwordHash;

    @BeforeAll
    void start() {
        String url = System.getProperty("test.database.url");
        assertThat(url).isNotBlank().startsWith("jdbc:postgresql:");
        schema = "api_" + UUID.randomUUID().toString().replace("-", "");
        source = new PGSimpleDataSource();
        source.setUrl(url);
        source.setUser(System.getProperty("test.database.username", "postgres"));
        source.setPassword(System.getProperty("test.database.password", ""));
        new JdbcTemplate(source).execute("CREATE SCHEMA " + schema);
        source.setCurrentSchema(schema);
        String schemaUrl = url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema;
        context =
                new SpringApplicationBuilder(Application.class)
                        .run(
                                "--server.port=0",
                                "--spring.datasource.url=" + schemaUrl,
                                "--spring.datasource.username=" + source.getUser(),
                                "--spring.datasource.password=" + source.getPassword(),
                                "--spring.flyway.schemas=" + schema,
                                "--spring.flyway.default-schema=" + schema,
                                "--spring.jpa.properties.hibernate.default_schema=" + schema,
                                "--app.auth.secret-base64="
                                        + Base64.getEncoder().encodeToString(new byte[32]),
                                "--app.bootstrap.login=",
                                "--logging.level.root=WARN");
        base =
                "http://localhost:"
                        + ((ServletWebServerApplicationContext) context).getWebServer().getPort();
        http = HttpClient.newHttpClient();
        json = context.getBean(ObjectMapper.class);
        users = context.getBean(UserAccountRepository.class);
        sessions = context.getBean(RefreshSessionRepository.class);
        passwordHash = context.getBean(PasswordEncoder.class).encode(PASSWORD);
    }

    @AfterAll
    void stop() {
        if (context != null) context.close();
        if (source != null && schema != null && schema.matches("api_[0-9a-f]{32}"))
            new JdbcTemplate(source).execute("DROP SCHEMA " + schema + " CASCADE");
    }

    @BeforeEach
    void accounts() {
        sessions.deleteAll();
        users.deleteAll();
        context.getBean(BookRequestRepository.class).deleteAll();
        context.getBean(ReaderRepository.class).deleteAll();
        account("admin", Role.ADMIN, false, true);
        account("librarian", Role.LIBRARIAN, false, true);
        account("viewer", Role.VIEWER, false, true);
        account("temporary", Role.ADMIN, true, true);
        account("disabled", Role.ADMIN, false, false);
    }

    private void account(String login, Role role, boolean temporary, boolean active) {
        UserAccount user = new UserAccount(login, login, passwordHash, Set.of(role));
        user.setMustChangePassword(temporary);
        user.setActive(active);
        users.saveAndFlush(user);
    }

    @Test
    void anonymousRequestsAndInvalidJwtReturnUnifiedErrors() throws Exception {
        Response anonymous = call("GET", "/api/readers", null, null);
        assertThat(anonymous.status()).isEqualTo(401);
        assertThat(anonymous.body().get("code").asText()).isEqualTo("UNAUTHORIZED");
        assertThat(anonymous.body().toString()).doesNotContain("trace", "password_hash");
        assertThat(call("GET", "/api/readers", null, "bad.jwt.token").status()).isEqualTo(401);
        assertThat(
                        call(
                                        "POST",
                                        "/api/auth/login",
                                        new LoginRequest("disabled", PASSWORD),
                                        null)
                                .status())
                .isEqualTo(401);
        assertThat(
                        call("POST", "/api/auth/login", new LoginRequest("missing", PASSWORD), null)
                                .status())
                .isEqualTo(401);
    }

    @Test
    void rotatesRefreshTokensAndRevokesAccessOnLogout() throws Exception {
        TokenResponse first = login("admin");
        assertThat(call("GET", "/api/auth/me", null, first.accessToken()).status()).isEqualTo(200);
        Response refreshed =
                call("POST", "/api/auth/refresh", new RefreshRequest(first.refreshToken()), null);
        assertThat(refreshed.status()).isEqualTo(200);
        TokenResponse next = json.treeToValue(refreshed.body(), TokenResponse.class);
        assertThat(next.refreshToken()).isNotEqualTo(first.refreshToken());
        assertThat(
                        call(
                                        "POST",
                                        "/api/auth/refresh",
                                        new RefreshRequest(first.refreshToken()),
                                        null)
                                .status())
                .isEqualTo(401);
        assertThat(
                        call(
                                        "POST",
                                        "/api/auth/logout",
                                        new RefreshRequest(next.refreshToken()),
                                        null)
                                .status())
                .isEqualTo(204);
        assertThat(call("GET", "/api/auth/me", null, first.accessToken()).status()).isEqualTo(401);
        assertThat(call("GET", "/api/auth/me", null, next.accessToken()).status()).isEqualTo(401);
        assertThat(
                        call(
                                        "POST",
                                        "/api/auth/refresh",
                                        new RefreshRequest(next.refreshToken()),
                                        null)
                                .status())
                .isEqualTo(401);
        var stored = sessions.findAll().getFirst();
        assertThat(stored.getTokenHash()).hasSize(64).isNotEqualTo(next.refreshToken());
        assertThat(first.toString()).doesNotContain(first.accessToken(), first.refreshToken());
    }

    @Test
    void requiresTemporaryPasswordChangeAndRevokesEveryOldSession() throws Exception {
        TokenResponse old = login("temporary");
        TokenResponse other = login("temporary");
        assertThat(old.user().mustChangePassword()).isTrue();
        Response blocked = call("GET", "/api/readers", null, old.accessToken());
        assertThat(blocked.status()).isEqualTo(403);
        assertThat(blocked.body().get("code").asText()).isEqualTo("PASSWORD_CHANGE_REQUIRED");
        assertThat(call("GET", "/api/auth/me", null, old.accessToken()).status()).isEqualTo(200);
        assertThat(
                        call(
                                        "POST",
                                        "/api/auth/change-password",
                                        new PasswordChangeRequest(PASSWORD, "short"),
                                        old.accessToken())
                                .status())
                .isEqualTo(400);
        assertThat(
                        call(
                                        "POST",
                                        "/api/auth/change-password",
                                        new PasswordChangeRequest(PASSWORD, "я".repeat(40)),
                                        old.accessToken())
                                .status())
                .isEqualTo(400);
        assertThat(
                        call(
                                        "POST",
                                        "/api/auth/change-password",
                                        new PasswordChangeRequest(
                                                "wrong-password", "Replacement123!"),
                                        old.accessToken())
                                .status())
                .isEqualTo(400);
        assertThat(
                        call(
                                        "POST",
                                        "/api/auth/change-password",
                                        new PasswordChangeRequest(PASSWORD, "Replacement123!"),
                                        old.accessToken())
                                .status())
                .isEqualTo(204);
        assertThat(call("GET", "/api/auth/me", null, old.accessToken()).status()).isEqualTo(401);
        assertThat(
                        call(
                                        "POST",
                                        "/api/auth/refresh",
                                        new RefreshRequest(other.refreshToken()),
                                        null)
                                .status())
                .isEqualTo(401);
        Response login =
                call(
                        "POST",
                        "/api/auth/login",
                        new LoginRequest("temporary", "Replacement123!"),
                        null);
        assertThat(login.status()).isEqualTo(200);
        assertThat(login.body().get("user").get("mustChangePassword").asBoolean()).isFalse();
        assertThat(users.findByLoginIgnoreCase("temporary").orElseThrow().getPasswordHash())
                .startsWith("$2")
                .isNotEqualTo("Replacement123!");
    }

    @Test
    void locksAccountAfterFailedAttemptsAndDoesNotRollbackCounter() throws Exception {
        for (int i = 0; i < 5; i++)
            assertThat(
                            call(
                                            "POST",
                                            "/api/auth/login",
                                            new LoginRequest("admin", "incorrect"),
                                            null)
                                    .status())
                    .isEqualTo(401);
        UserAccount user = users.findByLoginIgnoreCase("admin").orElseThrow();
        assertThat(user.getFailedAttempts()).isEqualTo(5);
        assertThat(user.getLockedUntil()).isAfter(Instant.now());
        assertThat(
                        call("POST", "/api/auth/login", new LoginRequest("admin", PASSWORD), null)
                                .status())
                .isEqualTo(401);
    }

    @Test
    void enforcesRolesAndRejectsDisabledAccountsWithExistingToken() throws Exception {
        TokenResponse admin = login("admin"),
                librarian = login("librarian"),
                viewer = login("viewer");
        ReaderCreate body = new ReaderCreate("Читатель", "reader@example.org", null, "LIB-0001");
        assertThat(call("POST", "/api/readers", body, viewer.accessToken()).status())
                .isEqualTo(403);
        assertThat(call("POST", "/api/readers", body, librarian.accessToken()).status())
                .isEqualTo(403);
        Response created = call("POST", "/api/readers", body, admin.accessToken());
        assertThat(created.status()).isEqualTo(201);
        UUID reader = json.treeToValue(created.body(), ReaderDto.class).id();
        BookRequestCreate request =
                new BookRequestCreate(
                        reader, "Книга", "Автор", null, null, null, RequestStatus.CREATED);
        assertThat(call("POST", "/api/book-requests", request, viewer.accessToken()).status())
                .isEqualTo(403);
        assertThat(call("POST", "/api/book-requests", request, librarian.accessToken()).status())
                .isEqualTo(201);
        assertThat(call("GET", "/api/book-requests", null, viewer.accessToken()).status())
                .isEqualTo(200);
        assertThat(call("GET", "/api/reports/statistics", null, viewer.accessToken()).status())
                .isEqualTo(200);
        UserAccount user = users.findByLoginIgnoreCase("viewer").orElseThrow();
        user.setActive(false);
        users.saveAndFlush(user);
        assertThat(call("GET", "/api/readers", null, viewer.accessToken()).status()).isEqualTo(401);
    }

    @Test
    void createsUpdatesAndValidatesDtosWithoutExposingJpaEntities() throws Exception {
        TokenResponse admin = login("admin");
        String token = admin.accessToken();
        Response bad =
                call("POST", "/api/readers", new ReaderCreate("", "invalid", null, "BAD"), token);
        assertThat(bad.status()).isEqualTo(400);
        assertThat(bad.body().get("fields").has("fullName")).isTrue();
        ReaderDto reader =
                json.treeToValue(
                        call(
                                        "POST",
                                        "/api/readers",
                                        new ReaderCreate(
                                                " Читатель ",
                                                "reader@example.org",
                                                null,
                                                "LIB-0001"),
                                        token)
                                .body(),
                        ReaderDto.class);
        Response created =
                call(
                        "POST",
                        "/api/book-requests",
                        new BookRequestCreate(
                                reader.id(),
                                "Книга",
                                "Автор",
                                null,
                                LocalDate.now().plusDays(7),
                                null,
                                RequestStatus.CREATED),
                        token);
        assertThat(created.status()).isEqualTo(201);
        assertThat(created.body().has("reader")).isFalse();
        BookRequestDto request = json.treeToValue(created.body(), BookRequestDto.class);
        assertThat(request.readerName()).isEqualTo("Читатель");
        assertThat(
                        call(
                                        "PUT",
                                        "/api/book-requests/" + request.id(),
                                        Map.of(
                                                "bookTitle",
                                                "Текст",
                                                "bookAuthor",
                                                "Автор",
                                                "status",
                                                "CREATED"),
                                        token)
                                .status())
                .isEqualTo(400);
        BookRequestUpdate update =
                new BookRequestUpdate(
                        request.version(),
                        "Изменено",
                        "Автор",
                        null,
                        request.desiredReturnDate(),
                        "Текст",
                        RequestStatus.APPROVED);
        Response updated = call("PUT", "/api/book-requests/" + request.id(), update, token);
        assertThat(updated.status()).isEqualTo(200);
        assertThat(updated.body().get("version").asLong()).isEqualTo(1);
        assertThat(call("PUT", "/api/book-requests/" + request.id(), update, token).status())
                .isEqualTo(409);
        assertThat(call("GET", "/api/book-requests/" + UUID.randomUUID(), null, token).status())
                .isEqualTo(404);
        assertThat(call("GET", "/api/book-requests/not-a-uuid", null, token).status())
                .isEqualTo(400);
        assertThat(call("GET", "/api/book-requests?sort=unsafe", null, token).status())
                .isEqualTo(400);
        assertThat(
                        call(
                                        "GET",
                                        "/api/book-requests?q="
                                                + java.net.URLEncoder.encode(
                                                        "Изменено",
                                                        java.nio.charset.StandardCharsets.UTF_8)
                                                + "&field=TITLE&sort=STATUS",
                                        null,
                                        token)
                                .body()
                                .size())
                .isEqualTo(1);
        assertThat(
                        call(
                                        "DELETE",
                                        "/api/book-requests/" + request.id() + "?version=0",
                                        null,
                                        token)
                                .status())
                .isEqualTo(409);
        assertThat(
                        call(
                                        "DELETE",
                                        "/api/book-requests/" + request.id() + "?version=1",
                                        null,
                                        token)
                                .status())
                .isEqualTo(204);
        assertThat(
                        call("DELETE", "/api/readers/" + reader.id() + "?version=0", null, token)
                                .status())
                .isEqualTo(204);
    }

    @Test
    void rollsBackCreationWithInvalidInitialStatus() throws Exception {
        String token = login("admin").accessToken();
        ReaderDto reader =
                json.treeToValue(
                        call(
                                        "POST",
                                        "/api/readers",
                                        new ReaderCreate(
                                                "Читатель", "reader@example.org", null, "LIB-0001"),
                                        token)
                                .body(),
                        ReaderDto.class);
        assertThat(
                        call(
                                        "POST",
                                        "/api/book-requests",
                                        new BookRequestCreate(
                                                reader.id(),
                                                "Не сохранить",
                                                "Автор",
                                                null,
                                                null,
                                                null,
                                                RequestStatus.ISSUED),
                                        token)
                                .status())
                .isEqualTo(400);
        assertThat(call("GET", "/api/book-requests", null, token).body().size()).isZero();
    }

    @Test
    void exposesSwaggerWithBearerScheme() throws Exception {
        HttpResponse<String> response =
                http.send(
                        HttpRequest.newBuilder(URI.create(base + "/v3/api-docs")).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode docs = json.readTree(response.body());
        assertThat(docs.path("components").path("securitySchemes").has("bearerAuth")).isTrue();
        assertThat(docs.path("paths").has("/api/auth/login")).isTrue();
        assertThat(docs.path("paths").has("/api/book-requests")).isTrue();
    }

    private TokenResponse login(String login) throws Exception {
        Response response =
                call("POST", "/api/auth/login", new LoginRequest(login, PASSWORD), null);
        assertThat(response.status()).isEqualTo(200);
        return json.treeToValue(response.body(), TokenResponse.class);
    }

    private Response call(String method, String path, Object body, String token) throws Exception {
        HttpRequest.Builder request =
                HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(30));
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (body != null) request.header("Content-Type", "application/json");
        HttpResponse<String> response =
                http.send(
                        request.method(
                                        method,
                                        body == null
                                                ? HttpRequest.BodyPublishers.noBody()
                                                : HttpRequest.BodyPublishers.ofString(
                                                        json.writeValueAsString(body)))
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
        return new Response(
                response.statusCode(),
                response.body().isBlank() ? json.nullNode() : json.readTree(response.body()));
    }

    private record Response(int status, JsonNode body) {}
}
