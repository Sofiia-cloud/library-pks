package ru.mirea.library;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceException;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import ru.mirea.library.domain.BookRequest;
import ru.mirea.library.domain.Reader;
import ru.mirea.library.model.RequestStatus;

import java.time.LocalDate;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PersistenceIT {

    private String schema;
    private String url;
    private String username;
    private String password;
    private PGSimpleDataSource dataSource;
    private JdbcTemplate jdbc;

    @BeforeEach
    void createSchema() {
        url = System.getProperty("test.database.url");
        assertThat(url).as("Pass -Dtest.database.url=jdbc:postgresql://host:port/test_database")
                .isNotBlank().startsWith("jdbc:postgresql:");
        username = System.getProperty("test.database.username", "postgres");
        password = System.getProperty("test.database.password", "");
        schema = "stage2_" + UUID.randomUUID().toString().replace("-", "");
        dataSource = new PGSimpleDataSource();
        dataSource.setUrl(url);
        dataSource.setUser(username);
        dataSource.setPassword(password);
        new JdbcTemplate(dataSource).execute("CREATE SCHEMA " + schema);
        dataSource.setCurrentSchema(schema);
        jdbc = new JdbcTemplate(dataSource);
    }

    @AfterEach
    void dropSchema() {
        if (dataSource != null && schema != null) {
            // Имя генерируется тестом, пользовательские строки в SQL не подставляются.
            if (!schema.matches("stage2_[0-9a-f]{32}")) throw new IllegalStateException("Unexpected schema");
            new JdbcTemplate(dataSource).execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    @Test
    void createsSchemaAndPersistsEntitiesThroughJpa() {
        withJpa(factory -> {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM readers", Integer.class)).isZero();
            EntityManager em = factory.createEntityManager();
            try {
                em.getTransaction().begin();
                Reader reader = new Reader("Тестовый читатель", "reader@example.org", null, "LIB-9001");
                em.persist(reader);
                BookRequest request = new BookRequest(reader, "Книга", "Автор", null,
                        LocalDate.of(2030, 1, 1), "Комментарий");
                em.persist(request);
                em.getTransaction().commit();
                assertThat(reader.getId()).isNotNull();
                assertThat(request.getId()).isNotNull();
                assertThat(reader.getReaderNumber()).isEqualTo(1);
                assertThat(request.getRequestNumber()).isEqualTo(1);
                UUID requestId = request.getId();
                em.clear();
                BookRequest saved = em.find(BookRequest.class, requestId);
                assertThat(saved.getReader().getId()).isEqualTo(reader.getId());
                assertThat(saved.getReader().getFullName()).isEqualTo("Тестовый читатель");
                assertThat(saved.getStatus()).isEqualTo(RequestStatus.CREATED);
                assertThat(saved.getRequestedCopies()).isEqualTo(1);
                assertThat(saved.isUrgent()).isFalse();
                assertThat(saved.isArchived()).isFalse();
                assertThat(saved.getVersion()).isZero();
                assertThat(saved.getCreatedAt()).isNotNull();
                assertThat(saved.getUpdatedAt()).isEqualTo(saved.getCreatedAt());
                assertThat(saved.getReader().isActive()).isTrue();
                em.getTransaction().begin();
                saved.setUrgent(true);
                saved.getReader().setActive(false);
                em.getTransaction().commit();
                assertThat(saved.getVersion()).isEqualTo(1);
                assertThat(saved.getReader().getVersion()).isEqualTo(1);
                assertThat(saved.getUpdatedAt()).isAfterOrEqualTo(saved.getCreatedAt());
            } finally {
                close(em);
            }
        });
    }

    @Test
    void upgradesLegacySchemaWithoutLosingDataOrReaderLinks() {
        new ResourceDatabasePopulator(new ClassPathResource("db/legacy-schema.sql")).execute(dataSource);
        jdbc.update("INSERT INTO readers(id, full_name, email, library_card_number) VALUES (100, ?, ?, ?)",
                "Явный номер", "number@example.org", "LIB-0100");
        jdbc.update("INSERT INTO book_requests(id, reader_id, book_title, book_author, status) "
                + "VALUES (250, 100, ?, ?, 'CREATED')", "Явный номер заявки", "Автор");
        var readersBefore = jdbc.queryForList("SELECT id AS reader_number, full_name, email, phone, "
                + "library_card_number, registered_at FROM readers ORDER BY id");
        var requestsBefore = jdbc.queryForList("SELECT id AS request_number, reader_id AS reader_number, "
                + "book_title, book_author, isbn, status, created_at, desired_return_date, comment "
                + "FROM book_requests ORDER BY id");
        Flyway flyway = flyway();
        assertThatThrownBy(flyway::migrate).isInstanceOf(FlywayException.class);
        flyway.baseline();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT reader_number, full_name, email, phone, library_card_number, "
                + "registered_at FROM readers ORDER BY reader_number")).isEqualTo(readersBefore);
        assertThat(jdbc.queryForList("SELECT br.request_number, r.reader_number, br.book_title, br.book_author, "
                + "br.isbn, br.status, br.created_at, br.desired_return_date, br.comment "
                + "FROM book_requests br JOIN readers r ON r.id = br.reader_id ORDER BY br.request_number"))
                .isEqualTo(requestsBefore);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM readers", Integer.class)).isEqualTo(6);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM book_requests", Integer.class)).isEqualTo(13);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM book_requests WHERE version = 0 "
                + "AND NOT archived AND NOT urgent AND requested_copies = 1 AND updated_at = created_at",
                Integer.class)).isEqualTo(13);
        var ids = jdbc.queryForList("SELECT id FROM book_requests ORDER BY request_number");
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(jdbc.queryForList("SELECT id FROM book_requests ORDER BY request_number")).isEqualTo(ids);
        withJpa(factory -> {
            EntityManager em = factory.createEntityManager();
            try {
                em.getTransaction().begin();
                Reader reader = new Reader("Новый", "next@example.org", null, "LIB-9002");
                em.persist(reader);
                BookRequest request = new BookRequest(reader, "Следующая", "Автор", null, null, null);
                em.persist(request);
                em.getTransaction().commit();
                assertThat(reader.getReaderNumber()).isGreaterThan(100);
                assertThat(request.getRequestNumber()).isGreaterThan(250);
            } finally {
                close(em);
            }
        });
    }

    @Test
    void rejectsConcurrentUpdateWithOldVersion() {
        withJpa(factory -> {
            UUID requestId = insertRequest(factory);
            EntityManager first = factory.createEntityManager();
            EntityManager second = factory.createEntityManager();
            try {
                first.getTransaction().begin();
                second.getTransaction().begin();
                BookRequest current = first.find(BookRequest.class, requestId);
                BookRequest stale = second.find(BookRequest.class, requestId);
                current.setBookTitle("Первое изменение");
                first.getTransaction().commit();
                stale.setBookTitle("Устаревшее изменение");
                assertThatThrownBy(() -> second.getTransaction().commit()).isInstanceOf(PersistenceException.class);
                assertThat(jdbc.queryForObject("SELECT book_title FROM book_requests WHERE id = ?",
                        String.class, requestId)).isEqualTo("Первое изменение");
                assertThat(jdbc.queryForObject("SELECT version FROM book_requests WHERE id = ?",
                        Long.class, requestId)).isEqualTo(1L);
            } finally {
                close(first);
                close(second);
            }
        });
    }

    @Test
    void enforcesDatabaseConstraints() {
        withJpa(factory -> {
            UUID requestId = insertRequest(factory);
            UUID readerId = jdbc.queryForObject("SELECT reader_id FROM book_requests WHERE id = ?",
                    UUID.class, requestId);
            assertThatThrownBy(() -> jdbc.update("UPDATE book_requests SET requested_copies = 0 WHERE id = ?",
                    requestId)).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("UPDATE book_requests SET status = 'UNKNOWN' WHERE id = ?",
                    requestId)).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("UPDATE book_requests SET version = -1 WHERE id = ?",
                    requestId)).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("INSERT INTO readers(full_name, email, library_card_number) "
                    + "VALUES ('Duplicate', 'reader@example.org', 'LIB-9999')"))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("DELETE FROM readers WHERE id = ?", readerId))
                    .isInstanceOf(DataIntegrityViolationException.class);
        });
    }

    private Flyway flyway() {
        return Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .baselineVersion("1").baselineOnMigrate(false).load();
    }

    private void withJpa(Consumer<EntityManagerFactory> action) {
        String schemaUrl = url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema;
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(EntityConfiguration.class)
                .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class,
                        FlywayAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
                        TransactionAutoConfiguration.class))
                .withPropertyValues("spring.datasource.url=" + schemaUrl,
                        "spring.datasource.username=" + username, "spring.datasource.password=" + password,
                        "spring.flyway.schemas=" + schema, "spring.flyway.default-schema=" + schema,
                        "spring.jpa.properties.hibernate.default_schema=" + schema)
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(EntityManagerFactory.class);
                    assertThat(context.getEnvironment().getProperty("spring.jpa.hibernate.ddl-auto"))
                            .isEqualTo("validate");
                    assertThat(context.getBean(Flyway.class).info().current().getVersion().toString()).isEqualTo("2");
                    action.accept(context.getBean(EntityManagerFactory.class));
                });
    }

    private UUID insertRequest(EntityManagerFactory factory) {
        EntityManager em = factory.createEntityManager();
        try {
            em.getTransaction().begin();
            Reader reader = new Reader("Читатель", "reader@example.org", null, "LIB-9001");
            em.persist(reader);
            BookRequest request = new BookRequest(reader, "Книга", "Автор", null, null, null);
            em.persist(request);
            em.getTransaction().commit();
            return request.getId();
        } finally {
            close(em);
        }
    }

    private void close(EntityManager em) {
        if (em.getTransaction().isActive()) em.getTransaction().rollback();
        em.close();
    }

    @Configuration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = {Reader.class, BookRequest.class})
    static class EntityConfiguration {
    }
}
