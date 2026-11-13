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
        ((java.util.Map<?, ?>)
                        org.springframework.test.util.ReflectionTestUtils.getField(
                                context.getBean(ru.mirea.library.security.LoginRateLimiter.class),
                                "attempts"))
                .clear();
        context.getBean(ImportBatchRepository.class).deleteAll();
        context.getBean(NoteRepository.class).deleteAll();
        context.getBean(AuditRepository.class).deleteAll();
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
                                .get("items")
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
        assertThat(call("GET", "/api/book-requests", null, token).body().get("items").size())
                .isZero();
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

    @Test
    void healthcheckIsPublicAndDoesNotExposeDatabaseDetails() throws Exception {
        var health = call("GET", "/actuator/health", null, null);
        assertThat(health.status()).isEqualTo(200);
        assertThat(health.body().get("status").asText()).isEqualTo("UP");
        assertThat(health.body().has("components")).isFalse();
        assertThat(call("GET", "/actuator/info", null, null).status()).isEqualTo(401);
        assertThat(call("GET", "/actuator/env", null, null).status()).isNotEqualTo(200);
    }

    private ReaderDto reference(String token) throws Exception {
        return json.treeToValue(
                call(
                                "POST",
                                "/api/readers",
                                new ReaderCreate(
                                        "Тестовый читатель",
                                        "reader@example.org",
                                        null,
                                        "LIB-0001"),
                                token)
                        .body(),
                ReaderDto.class);
    }

    private BookRequestDto item(
            String token, UUID reader, String title, int copies, boolean urgent, Set<String> tags)
            throws Exception {
        Response r =
                call(
                        "POST",
                        "/api/book-requests",
                        new BookRequestCreate(
                                reader,
                                title,
                                "Автор",
                                null,
                                LocalDate.now().plusDays(8),
                                null,
                                RequestStatus.CREATED,
                                copies,
                                urgent,
                                tags,
                                null),
                        token);
        assertThat(r.status()).as(r.body().toString()).isEqualTo(201);
        return json.treeToValue(r.body(), BookRequestDto.class);
    }

    @Test
    void pagesFiltersAndAnalyticsUseTheSameDomainValues() throws Exception {
        String token = login("admin").accessToken();
        var reader = reference(token);
        var one = item(token, reader.id(), "Alpha", 4, true, Set.of(" Учёба "));
        item(token, reader.id(), "Beta", 1, false, Set.of());
        item(token, reader.id(), "Gamma", 6, true, Set.of("учёба"));
        Response page = call("GET", "/api/book-requests?size=1&page=1&sort=TITLE", null, token);
        assertThat(page.status()).isEqualTo(200);
        assertThat(page.body().get("items").get(0).get("bookTitle").asText()).isEqualTo("Beta");
        assertThat(page.body().get("totalElements").asInt()).isEqualTo(3);
        String tag = java.net.URLEncoder.encode("учёба", java.nio.charset.StandardCharsets.UTF_8);
        Response filtered =
                call(
                        "GET",
                        "/api/book-requests?urgent=true&tag="
                                + tag
                                + "&copiesOp=GE&copiesValue=5&readerId="
                                + reader.id(),
                        null,
                        token);
        assertThat(filtered.body().get("totalElements").asInt()).isEqualTo(1);
        assertThat(call("GET", "/api/book-requests?size=101", null, token).status()).isEqualTo(400);
        assertThat(call("GET", "/api/book-requests?scope=WRONG", null, token).status())
                .isEqualTo(400);
        assertThat(call("GET", "/api/book-requests?copiesOp=GE", null, token).status())
                .isEqualTo(400);
        assertThat(
                        call("GET", "/api/book-requests?q=%27%20OR%201%3D1--", null, token)
                                .body()
                                .get("totalElements")
                                .asInt())
                .isZero();
        var approved =
                json.treeToValue(
                        call(
                                        "PUT",
                                        "/api/book-requests/" + one.id() + "/status",
                                        new StatusUpdate(one.version(), RequestStatus.APPROVED),
                                        token)
                                .body(),
                        BookRequestDto.class);
        var issued =
                json.treeToValue(
                        call(
                                        "PUT",
                                        "/api/book-requests/" + one.id() + "/status",
                                        new StatusUpdate(approved.version(), RequestStatus.ISSUED),
                                        token)
                                .body(),
                        BookRequestDto.class);
        new JdbcTemplate(source)
                .update(
                        "UPDATE book_requests SET desired_return_date=? WHERE id=?",
                        LocalDate.now().minusDays(1),
                        issued.id());
        assertThat(
                        call("GET", "/api/book-requests?returnState=OVERDUE", null, token)
                                .body()
                                .get("totalElements")
                                .asInt())
                .isEqualTo(1);
        var analytics = call("GET", "/api/analytics/summary", null, token);
        assertThat(analytics.status()).isEqualTo(200);
        assertThat(analytics.body().get("total").asInt()).isEqualTo(3);
        assertThat(analytics.body().get("urgent").asInt()).isEqualTo(2);
        assertThat(analytics.body().get("averageCopies").asDouble())
                .isCloseTo(11.0 / 3, org.assertj.core.data.Offset.offset(.0001));
        assertThat(analytics.body().get("byReturnState").get("OVERDUE").asInt()).isEqualTo(1);
    }

    @Test
    void archivesRestoresAndPermanentlyDeletesOnlyAsAdministrator() throws Exception {
        String admin = login("admin").accessToken(), lib = login("librarian").accessToken();
        var reader = reference(admin);
        var item = item(lib, reader.id(), "В архив", 2, false, Set.of("тег"));
        assertThat(
                        call(
                                        "DELETE",
                                        "/api/book-requests/"
                                                + item.id()
                                                + "?version="
                                                + item.version(),
                                        null,
                                        lib)
                                .status())
                .isEqualTo(204);
        var archived =
                json.treeToValue(
                        call("GET", "/api/book-requests/" + item.id(), null, lib).body(),
                        BookRequestDto.class);
        assertThat(archived.archived()).isTrue();
        assertThat(call("GET", "/api/book-requests", null, lib).body().get("totalElements").asInt())
                .isZero();
        assertThat(
                        call("GET", "/api/book-requests?scope=ARCHIVED", null, lib)
                                .body()
                                .get("totalElements")
                                .asInt())
                .isEqualTo(1);
        assertThat(
                        call(
                                        "POST",
                                        "/api/book-requests/" + item.id() + "/restore",
                                        new VersionRequest(item.version()),
                                        lib)
                                .status())
                .isEqualTo(409);
        var restored =
                json.treeToValue(
                        call(
                                        "POST",
                                        "/api/book-requests/" + item.id() + "/restore",
                                        new VersionRequest(archived.version()),
                                        lib)
                                .body(),
                        BookRequestDto.class);
        assertThat(restored.archived()).isFalse();
        assertThat(
                        call(
                                        "DELETE",
                                        "/api/book-requests/"
                                                + item.id()
                                                + "/permanent?version="
                                                + restored.version(),
                                        null,
                                        admin)
                                .status())
                .isEqualTo(400);
        call(
                "DELETE",
                "/api/book-requests/" + item.id() + "?version=" + restored.version(),
                null,
                lib);
        archived =
                json.treeToValue(
                        call("GET", "/api/book-requests/" + item.id(), null, lib).body(),
                        BookRequestDto.class);
        assertThat(
                        call(
                                        "DELETE",
                                        "/api/book-requests/"
                                                + item.id()
                                                + "/permanent?version="
                                                + archived.version(),
                                        null,
                                        lib)
                                .status())
                .isEqualTo(403);
        assertThat(
                        call(
                                        "DELETE",
                                        "/api/book-requests/"
                                                + item.id()
                                                + "/permanent?version="
                                                + archived.version(),
                                        null,
                                        admin)
                                .status())
                .isEqualTo(204);
        assertThat(call("GET", "/api/book-requests/" + item.id(), null, admin).status())
                .isEqualTo(404);
        assertThat(
                        call(
                                        "PUT",
                                        "/api/readers/" + reader.id() + "/active",
                                        new ActiveUpdate(reader.version(), false),
                                        admin)
                                .status())
                .isEqualTo(200);
        assertThat(
                        call(
                                        "POST",
                                        "/api/book-requests",
                                        new BookRequestCreate(
                                                reader.id(),
                                                "Не создать",
                                                "Автор",
                                                null,
                                                null,
                                                null,
                                                RequestStatus.CREATED),
                                        lib)
                                .status())
                .isEqualTo(400);
    }

    @Test
    void notesPreserveAuthorAndEnforceOwnershipAndVersion() throws Exception {
        String admin = login("admin").accessToken(),
                lib = login("librarian").accessToken(),
                viewer = login("viewer").accessToken();
        var reader = reference(admin);
        var item = item(lib, reader.id(), "Заметки", 1, false, Set.of());
        String path = "/api/book-requests/" + item.id() + "/notes";
        assertThat(call("POST", path, new NoteWrite(null, "Заметка", true), viewer).status())
                .isEqualTo(403);
        var note =
                json.treeToValue(
                        call("POST", path, new NoteWrite(null, "Заметка", true), lib).body(),
                        NoteDto.class);
        account("other.editor", Role.LIBRARIAN, false, true);
        String other = login("other.editor").accessToken();
        assertThat(
                        call(
                                        "PUT",
                                        path + "/" + note.id(),
                                        new NoteWrite(note.version(), "Чужая правка", false),
                                        other)
                                .status())
                .isEqualTo(403);
        assertThat(
                        call(
                                        "DELETE",
                                        path + "/" + note.id() + "?version=" + note.version(),
                                        null,
                                        other)
                                .status())
                .isEqualTo(403);
        assertThat(note.authorLogin()).isEqualTo("librarian");
        assertThat(note.pinned()).isTrue();
        assertThat(call("GET", path, null, viewer).body().size()).isEqualTo(1);
        var changed =
                call(
                        "PUT",
                        path + "/" + note.id(),
                        new NoteWrite(note.version(), "Правка", false),
                        admin);
        assertThat(changed.status()).isEqualTo(200);
        assertThat(
                        call(
                                        "PUT",
                                        path + "/" + note.id(),
                                        new NoteWrite(note.version(), "Старая правка", false),
                                        lib)
                                .status())
                .isEqualTo(409);
        assertThat(
                        call(
                                        "DELETE",
                                        path
                                                + "/"
                                                + note.id()
                                                + "?version="
                                                + changed.body().get("version").asLong(),
                                        null,
                                        admin)
                                .status())
                .isEqualTo(204);
    }

    @Test
    void bulkCommitsValidItemsAndReportsStaleAndMissingItems() throws Exception {
        String admin = login("admin").accessToken(), lib = login("librarian").accessToken();
        var reader = reference(admin);
        var one = item(lib, reader.id(), "Первая", 1, false, Set.of());
        var two = item(lib, reader.id(), "Вторая", 1, false, Set.of());
        var result =
                call(
                        "POST",
                        "/api/book-requests/bulk",
                        new BulkRequest(
                                List.of(
                                        new VersionedId(one.id(), one.version()),
                                        new VersionedId(two.id(), two.version() + 1),
                                        new VersionedId(UUID.randomUUID(), 0L)),
                                BulkAction.ARCHIVE,
                                null,
                                null),
                        lib);
        assertThat(result.status()).isEqualTo(200);
        assertThat(result.body().get("successful").asInt()).isEqualTo(1);
        assertThat(result.body().get("failed").asInt()).isEqualTo(2);
        assertThat(
                        call("GET", "/api/book-requests/" + one.id(), null, lib)
                                .body()
                                .get("archived")
                                .asBoolean())
                .isTrue();
        assertThat(
                        call("GET", "/api/book-requests/" + two.id(), null, lib)
                                .body()
                                .get("archived")
                                .asBoolean())
                .isFalse();
        var tags =
                call(
                        "POST",
                        "/api/book-requests/bulk",
                        new BulkRequest(
                                List.of(new VersionedId(two.id(), two.version())),
                                BulkAction.ADD_TAG,
                                null,
                                "Тест"),
                        lib);
        assertThat(tags.body().get("successful").asInt()).isEqualTo(1);
        var tagged =
                json.treeToValue(
                        call("GET", "/api/book-requests/" + two.id(), null, lib).body(),
                        BookRequestDto.class);
        assertThat(tagged.tags()).containsExactly("тест");
        assertThat(tagged.version()).isGreaterThan(two.version());
        assertThat(
                        call(
                                        "POST",
                                        "/api/book-requests/bulk",
                                        new BulkRequest(
                                                List.of(new VersionedId(two.id(), two.version())),
                                                BulkAction.REMOVE_TAG,
                                                null,
                                                "тест"),
                                        lib)
                                .body()
                                .get("failed")
                                .asInt())
                .isEqualTo(1);
    }

    @Test
    void administratorsCreateResetAndDisableUsersWithoutReturningPasswords() throws Exception {
        String admin = login("admin").accessToken(), viewer = login("viewer").accessToken();
        assertThat(call("GET", "/api/admin/users", null, viewer).status()).isEqualTo(403);
        var created =
                call(
                        "POST",
                        "/api/admin/users",
                        new AdminUserCreate(
                                "new.user", "Новый", Set.of(Role.LIBRARIAN), "TemporaryPass123!"),
                        admin);
        assertThat(created.status()).isEqualTo(200);
        assertThat(created.body().toString())
                .doesNotContain("TemporaryPass123!", "passwordHash", "$2");
        var u = json.treeToValue(created.body(), AdminUserDto.class);
        var temp =
                json.treeToValue(
                        call(
                                        "POST",
                                        "/api/auth/login",
                                        new LoginRequest(u.login(), "TemporaryPass123!"),
                                        null)
                                .body(),
                        TokenResponse.class);
        assertThat(temp.user().mustChangePassword()).isTrue();
        var reset =
                call(
                        "POST",
                        "/api/admin/users/" + u.id() + "/reset-password",
                        new PasswordReset(u.version(), "ResetPassword123!"),
                        admin);
        assertThat(reset.status()).isEqualTo(200);
        assertThat(call("GET", "/api/auth/me", null, temp.accessToken()).status()).isEqualTo(401);
        u = json.treeToValue(reset.body(), AdminUserDto.class);
        var disabled =
                call(
                        "PUT",
                        "/api/admin/users/" + u.id(),
                        new AdminUserUpdate(
                                u.version(),
                                "renamed.user",
                                "Новое имя",
                                Set.of(Role.VIEWER),
                                false),
                        admin);
        assertThat(disabled.status()).isEqualTo(200);
        assertThat(
                        call(
                                        "POST",
                                        "/api/auth/login",
                                        new LoginRequest("renamed.user", "ResetPassword123!"),
                                        null)
                                .status())
                .isEqualTo(401);
        assertThat(call("GET", "/api/admin/users?size=1", null, admin).body().get("items").size())
                .isEqualTo(1);
        assertThat(call("GET", "/api/admin/users?size=101", null, admin).status()).isEqualTo(400);
        var self = users.findByLoginIgnoreCase("admin").orElseThrow();
        assertThat(
                        call(
                                        "PUT",
                                        "/api/admin/users/" + self.getId(),
                                        new AdminUserUpdate(
                                                self.getVersion(),
                                                "admin",
                                                "admin",
                                                Set.of(Role.VIEWER),
                                                true),
                                        admin)
                                .status())
                .isEqualTo(400);
    }

    @Test
    void auditIsAdminOnlySearchableAndCarriesCorrelationId() throws Exception {
        String admin = login("admin").accessToken(), viewer = login("viewer").accessToken();
        var reader = reference(admin);
        String cid = "test-correlation-123";
        var response =
                http.send(
                        HttpRequest.newBuilder(URI.create(base + "/api/book-requests"))
                                .header("Authorization", "Bearer " + admin)
                                .header("X-Correlation-ID", cid)
                                .header("Content-Type", "application/json")
                                .POST(
                                        HttpRequest.BodyPublishers.ofString(
                                                json.writeValueAsString(
                                                        new BookRequestCreate(
                                                                reader.id(),
                                                                "Аудит",
                                                                "Автор",
                                                                null,
                                                                null,
                                                                null,
                                                                RequestStatus.CREATED))))
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.headers().firstValue("X-Correlation-ID")).contains(cid);
        assertThat(call("GET", "/api/admin/audit", null, viewer).status()).isEqualTo(403);
        var audit =
                call("GET", "/api/admin/audit?q=" + cid + "&sort=actorLogin&size=1", null, admin);
        assertThat(audit.status()).isEqualTo(200);
        assertThat(audit.body().get("items").get(0).get("correlationId").asText()).isEqualTo(cid);
        assertThat(audit.body().toString()).doesNotContain(PASSWORD, "accessToken", "passwordHash");
        assertThat(call("GET", "/api/admin/audit?sort=unsafe", null, admin).status())
                .isEqualTo(400);
    }

    private static final String CSV_HEADER =
            "externalCode,readerCard,bookTitle,bookAuthor,isbn,status,desiredReturnDate,comment,requestedCopies,urgent,tags\r\n";

    @Test
    void previewsCsvWithoutMutatingDataAppliesOnceAndRejectsStaleUpdates() throws Exception {
        String admin = login("admin").accessToken(), lib = login("librarian").accessToken();
        reference(admin);
        String content =
                CSV_HEADER + "EXT-01,LIB-0001,Импорт,Автор,,CREATED,,,3,true,учёба|java\r\n";
        var checked =
                call(
                        "POST",
                        "/api/book-requests/import/preview",
                        new CsvPreviewRequest(content, ImportMode.NEW_ONLY),
                        lib);
        assertThat(checked.status()).as(checked.body().toString()).isEqualTo(200);
        var preview = json.treeToValue(checked.body(), CsvPreview.class);
        assertThat(preview.validRows()).isEqualTo(1);
        assertThat(preview.invalidRows()).isZero();
        assertThat(call("GET", "/api/book-requests", null, lib).body().get("totalElements").asInt())
                .isZero();
        assertThat(
                        call(
                                        "POST",
                                        "/api/book-requests/import/apply",
                                        new CsvApply(preview.id()),
                                        admin)
                                .status())
                .isEqualTo(403);
        var applied =
                call("POST", "/api/book-requests/import/apply", new CsvApply(preview.id()), lib);
        assertThat(applied.status()).as(applied.body().toString()).isEqualTo(200);
        assertThat(applied.body().get("created").asInt()).isEqualTo(1);
        assertThat(
                        call(
                                        "POST",
                                        "/api/book-requests/import/apply",
                                        new CsvApply(preview.id()),
                                        lib)
                                .body())
                .isEqualTo(applied.body());
        var page = call("GET", "/api/book-requests", null, lib);
        var item = json.treeToValue(page.body().get("items").get(0), BookRequestDto.class);
        assertThat(item.externalCode()).isEqualTo("EXT-01");
        assertThat(item.tags()).containsExactlyInAnyOrder("учёба", "java");
        var up =
                json.treeToValue(
                        call(
                                        "POST",
                                        "/api/book-requests/import/preview",
                                        new CsvPreviewRequest(
                                                content.replace("Импорт", "Обновлено"),
                                                ImportMode.UPSERT),
                                        lib)
                                .body(),
                        CsvPreview.class);
        assertThat(
                        call("POST", "/api/book-requests/import/apply", new CsvApply(up.id()), lib)
                                .body()
                                .get("updated")
                                .asInt())
                .isEqualTo(1);
        var stale =
                json.treeToValue(
                        call(
                                        "POST",
                                        "/api/book-requests/import/preview",
                                        new CsvPreviewRequest(content, ImportMode.UPSERT),
                                        lib)
                                .body(),
                        CsvPreview.class);
        var current =
                json.treeToValue(
                        call("GET", "/api/book-requests/" + item.id(), null, lib).body(),
                        BookRequestDto.class);
        call(
                "PUT",
                "/api/book-requests/" + item.id() + "/status",
                new StatusUpdate(current.version(), RequestStatus.APPROVED),
                lib);
        assertThat(
                        call(
                                        "POST",
                                        "/api/book-requests/import/apply",
                                        new CsvApply(stale.id()),
                                        lib)
                                .status())
                .isEqualTo(409);
        var bad =
                json.treeToValue(
                        call(
                                        "POST",
                                        "/api/book-requests/import/preview",
                                        new CsvPreviewRequest(content, ImportMode.NEW_ONLY),
                                        lib)
                                .body(),
                        CsvPreview.class);
        assertThat(bad.invalidRows()).isEqualTo(1);
        assertThat(
                        call("POST", "/api/book-requests/import/apply", new CsvApply(bad.id()), lib)
                                .status())
                .isEqualTo(400);
    }

    @Test
    void csvExportsRespectFilterAndRoundTripQuotedValues() throws Exception {
        String token = login("admin").accessToken();
        var reader = reference(token);
        item(token, reader.id(), "=SUM(1,2) \"цитата\"\nстрока", 2, true, Set.of("csv"));
        item(token, reader.id(), "Обычная", 1, false, Set.of());
        var response =
                http.send(
                        HttpRequest.newBuilder(
                                        URI.create(base + "/api/book-requests/export?urgent=true"))
                                .header("Authorization", "Bearer " + token)
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("'=SUM").doesNotContain("Обычная");
        var preview =
                json.treeToValue(
                        call(
                                        "POST",
                                        "/api/book-requests/import/preview",
                                        new CsvPreviewRequest(response.body(), ImportMode.UPSERT),
                                        token)
                                .body(),
                        CsvPreview.class);
        assertThat(preview.validRows()).isEqualTo(1);
        assertThat(preview.invalidRows()).isZero();
        assertThat(
                        call(
                                        "POST",
                                        "/api/book-requests/import/apply",
                                        new CsvApply(preview.id()),
                                        token)
                                .status())
                .isEqualTo(200);
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
