package ru.mirea.library;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.mirea.library.domain.BookRequest;
import ru.mirea.library.domain.Reader;
import ru.mirea.library.error.BusinessException;
import ru.mirea.library.error.EntityNotFoundException;
import ru.mirea.library.model.RequestStatus;
import ru.mirea.library.repository.ReaderRepository;
import ru.mirea.library.service.BookRequestService;
import ru.mirea.library.service.ReaderService;
import ru.mirea.library.service.ReportService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServicesIT {
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
        schema = "stage3_" + UUID.randomUUID().toString().replace("-", "");
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
            if (!schema.matches("stage3_[0-9a-f]{32}")) throw new IllegalStateException("Unexpected schema");
            new JdbcTemplate(dataSource).execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    @Test
    void normalizesReaderFieldsAndChecksUniquenessBeforeSaving() {
        withServices(services -> {
            Reader reader = services.readers.create("  Иванов Иван  ", "  Reader@Example.ORG  ", "  +79001112233  ", " LIB-0001 ");
            assertThat(reader.getFullName()).isEqualTo("Иванов Иван");
            assertThat(reader.getEmail()).isEqualTo("reader@example.org");
            assertThat(reader.getPhone()).isEqualTo("+79001112233");
            assertThat(reader.getLibraryCardNumber()).isEqualTo("LIB-0001");
            assertThatThrownBy(() -> services.readers.create("Другой", "READER@example.org", null, "LIB-0002"))
                    .isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> services.readers.create("Другой", "other@example.org", null, " LIB-0001 "))
                    .isInstanceOf(BusinessException.class);
            Reader updated = services.readers.update(reader.getId(), 0, " Новое имя ", "READER@example.org", " ", "LIB-0001");
            assertThat(updated.getPhone()).isNull();
            assertThat(updated.getVersion()).isEqualTo(1);
            assertThatThrownBy(() -> services.readers.update(reader.getId(), 0, "Устаревшее", "reader@example.org", null, "LIB-0001"))
                    .isInstanceOf(ObjectOptimisticLockingFailureException.class);
            assertThat(services.readers.getAll()).hasSize(1);
            assertThat(services.readers.getById(reader.getId()).getFullName()).isEqualTo("Новое имя");
        });
    }

    @Test
    void validatesRequiredFieldsLengthsAndMissingRecords() {
        withServices(services -> {
            assertThatThrownBy(() -> services.readers.create(" ", "a@example.org", null, "LIB-0001"))
                    .isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> services.readers.create("Имя", "invalid", null, "LIB-0001"))
                    .isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> services.readers.create("Имя", "a@example.org", "123", "LIB-0001"))
                    .isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> services.readers.create("Имя", "a@example.org", null, "BAD"))
                    .isInstanceOf(BusinessException.class);
            Reader reader = reader(services, 1);
            assertThatThrownBy(() -> services.requests.create(reader.getId(), " ", "Автор", null, null, null))
                    .isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> services.requests.create(reader.getId(), "a".repeat(201), "Автор", null, null, null))
                    .isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> services.requests.create(reader.getId(), "Книга", "Автор", "1".repeat(21), null, null))
                    .isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> services.requests.create(reader.getId(), "Книга", "Автор", null, LocalDate.now().minusDays(1), null))
                    .isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> services.requests.getById(UUID.randomUUID())).isInstanceOf(EntityNotFoundException.class);
            assertThatThrownBy(() -> services.readers.getById(UUID.randomUUID())).isInstanceOf(EntityNotFoundException.class);
            assertThat(services.requests.getAll()).isEmpty();
        });
    }

    @Test
    void updatesFieldsAndStatusAtomicallyAndChecksVersions() {
        withServices(services -> {
            Reader reader = reader(services, 1);
            BookRequest request = request(services, reader, "Первая книга");
            assertThatThrownBy(() -> services.requests.update(request.getId(), 0, "Не сохранять", "Автор", null,
                    null, "Изменение", RequestStatus.ISSUED)).isInstanceOf(BusinessException.class);
            assertThat(services.requests.getById(request.getId()).getBookTitle()).isEqualTo("Первая книга");
            assertThat(services.requests.getById(request.getId()).getVersion()).isZero();
            BookRequest updated = services.requests.update(request.getId(), 0, " Новая книга ", " Новый автор ", " ",
                    LocalDate.now().plusDays(7), " Заметка ", RequestStatus.APPROVED);
            assertThat(updated.getBookTitle()).isEqualTo("Новая книга");
            assertThat(updated.getStatus()).isEqualTo(RequestStatus.APPROVED);
            assertThat(updated.getVersion()).isEqualTo(1);
            assertThat(updated.getIsbn()).isNull();
            assertThat(updated.getComment()).isEqualTo("Заметка");
            assertThat(services.requests.getById(request.getId()).getReader().getFullName()).isEqualTo(reader.getFullName());
            assertThatThrownBy(() -> services.requests.changeStatus(request.getId(), 0, RequestStatus.ISSUED))
                    .isInstanceOf(ObjectOptimisticLockingFailureException.class);
            assertThatThrownBy(() -> services.requests.changeStatus(request.getId(), 1, null)).isInstanceOf(BusinessException.class);
            BookRequest issued = services.requests.changeStatus(request.getId(), 1, RequestStatus.ISSUED);
            assertThat(issued.getVersion()).isEqualTo(2);
            assertThatThrownBy(() -> services.requests.changeStatus(request.getId(), 2, RequestStatus.CANCELLED))
                    .isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> services.requests.delete(request.getId(), 2)).isInstanceOf(BusinessException.class);
            assertThat(services.requests.changeStatus(request.getId(), 2, RequestStatus.RETURNED).getVersion()).isEqualTo(3);
        });
    }

    @Test
    void allowsEditingHistoricalReturnDateButRejectsNewPastDate() {
        withServices(services -> {
            BookRequest request = request(services, reader(services, 1), "Историческая");
            LocalDate oldDate = LocalDate.now().minusDays(10);
            jdbc.update("UPDATE book_requests SET desired_return_date = ? WHERE id = ?", oldDate, request.getId());
            BookRequest updated = services.requests.update(request.getId(), 0, "Новое название", "Автор", null,
                    oldDate, null, RequestStatus.CREATED);
            assertThat(updated.getDesiredReturnDate()).isEqualTo(oldDate);
            assertThatThrownBy(() -> services.requests.update(request.getId(), 1, "Не сохранять", "Автор", null,
                    oldDate.minusDays(1), null, RequestStatus.CREATED)).isInstanceOf(BusinessException.class);
            assertThat(services.requests.getById(request.getId()).getBookTitle()).isEqualTo("Новое название");
        });
    }

    @Test
    void disablesReadersWithoutLosingLinkedRequests() {
        withServices(services -> {
            Reader reader = reader(services, 1);
            BookRequest request = request(services, reader, "Книга");
            services.readers.delete(reader.getId(), 0);
            assertThat(services.readers.getById(reader.getId()).isActive()).isFalse();
            assertThat(services.readers.existsById(reader.getId())).isTrue();
            assertThatThrownBy(() -> request(services, reader, "Не создавать")).isInstanceOf(BusinessException.class);
            BookRequest cancelled = services.requests.changeStatus(request.getId(), 0, RequestStatus.CANCELLED);
            services.requests.delete(request.getId(), cancelled.getVersion());
            assertThat(services.requests.getById(request.getId()).isArchived()).isTrue();
            Reader enabled = services.readers.setActive(reader.getId(), 1, true);
            assertThat(enabled.isActive()).isTrue();
            assertThat(request(services, enabled, "Новая заявка")).isNotNull();
        });
    }

    @Test
    void searchesCaseInsensitivelyAndTreatsWildcardsAsLiteralText() {
        withServices(services -> {
            Reader reader = reader(services, 1);
            BookRequest literal = request(services, reader, "Скидка 100%_!");
            request(services, reader, "Скидка 100XX");
            assertThat(services.requests.searchByTitle("СКИДКА")).hasSize(2);
            assertThat(services.requests.searchByTitle("%_!")).extracting(BookRequest::getId).containsExactly(literal.getId());
            assertThat(services.requests.searchByTitle("' OR 1=1 --")).isEmpty();
            assertThat(services.requests.searchByAuthor("АВТОР")).hasSize(2);
            assertThat(services.requests.searchByReaderName("ЧИТАТЕЛЬ")).hasSize(2);
            assertThat(services.requests.findByReaderCardNumber("LIB-0001")).hasSize(2);
            assertThat(services.requests.findByReaderId(reader.getId())).hasSize(2);
            assertThat(services.requests.searchByTitle(" ")).hasSize(2);
        });
    }

    @Test
    void combinesFiltersAndSortsInDatabaseInDomainStatusOrder() {
        withServices(services -> {
            Reader one = reader(services, 1);
            Reader two = reader(services, 2);
            BookRequest created = request(services, two, "zeta");
            BookRequest approved = request(services, one, "Alpha");
            approved = services.requests.changeStatus(approved.getId(), 0, RequestStatus.APPROVED);
            BookRequest issued = request(services, one, "beta");
            services.requests.changeStatus(issued.getId(), 0, RequestStatus.APPROVED);
            services.requests.changeStatus(issued.getId(), 1, RequestStatus.ISSUED);
            LocalDateTime firstDate = LocalDateTime.of(2024, 1, 1, 10, 0);
            LocalDateTime secondDate = firstDate.plusDays(1);
            jdbc.update("UPDATE book_requests SET created_at = ? WHERE id = ?", firstDate, created.getId());
            jdbc.update("UPDATE book_requests SET created_at = ? WHERE id IN (?, ?)", secondDate, approved.getId(), issued.getId());
            assertThat(services.requests.sortByStatus(true)).extracting(BookRequest::getStatus)
                    .containsExactly(RequestStatus.CREATED, RequestStatus.APPROVED, RequestStatus.ISSUED);
            assertThat(services.requests.sortByStatus(false)).extracting(BookRequest::getStatus)
                    .containsExactly(RequestStatus.ISSUED, RequestStatus.APPROVED, RequestStatus.CREATED);
            assertThat(services.requests.sortByBookTitle(true)).extracting(BookRequest::getBookTitle).containsExactly("Alpha", "beta", "zeta");
            assertThat(services.requests.sortByBookTitle(false)).extracting(BookRequest::getBookTitle).containsExactly("zeta", "beta", "Alpha");
            assertThat(services.requests.sortByCreatedAt(true)).extracting(BookRequest::getId)
                    .containsExactly(created.getId(), approved.getId(), issued.getId());
            assertThat(services.requests.findByStatus(RequestStatus.APPROVED)).hasSize(1);
            assertThat(services.requests.filter(one.getId(), RequestStatus.APPROVED, firstDate, secondDate))
                    .extracting(BookRequest::getId).containsExactly(approved.getId());
            assertThat(services.requests.findByDateRange(firstDate, firstDate)).hasSize(1);
            assertThatThrownBy(() -> services.requests.findByDateRange(secondDate, firstDate)).isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> services.requests.filter(null, null, firstDate, null)).isInstanceOf(BusinessException.class);
        });
    }

    @Test
    void reportsEmptyDatabaseWithoutDivisionErrors() {
        withServices(services -> {
            assertThat(services.reports.summary().values()).containsOnly(0L);
            assertThat(services.reports.averageRequestsPerReader()).isZero();
            assertThat(services.reports.countGroupedByStatus()).hasSize(6);
            assertThat(services.reports.countGroupedByStatus().values()).containsOnly(0L);
            assertThat(services.reports.requestsByMonth(2024)).hasSize(12);
            assertThat(services.reports.requestsByMonth(2024).values()).containsOnly(0L);
            assertThat(services.reports.topBooks(5)).isEmpty();
            assertThat(services.reports.topAuthors(5)).isEmpty();
            assertThat(services.reports.topReaders(5)).isEmpty();
            assertThat(services.reports.readerActivity()).isEmpty();
            assertThat(services.reports.requestsForExport()).isEmpty();
            assertThatThrownBy(() -> services.reports.topBooks(0)).isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> services.reports.requestsByMonth(2101)).isInstanceOf(BusinessException.class);
        });
    }

    @Test
    void calculatesAllLegacyReportsAndKeepsReadersWithEqualNamesSeparate() {
        withServices(services -> {
            Reader one = services.readers.create("Одинаковое ФИО", "one@example.org", null, "LIB-0001");
            Reader two = services.readers.create("Одинаковое ФИО", "two@example.org", null, "LIB-0002");
            Reader idle = reader(services, 3);
            seedRequest(one, "A", "X", RequestStatus.CREATED, LocalDateTime.of(2024, 1, 1, 0, 0), null);
            seedRequest(one, "A", "X", RequestStatus.ISSUED, LocalDateTime.now().minusDays(10), LocalDate.now().minusDays(1));
            seedRequest(two, "B", "Y", RequestStatus.RETURNED, LocalDateTime.now().minusDays(40), null);
            seedRequest(two, "C", "Y", RequestStatus.CANCELLED, LocalDateTime.of(2024, 12, 31, 23, 59), null);
            seedRequest(two, "D", "Z", RequestStatus.REJECTED, LocalDateTime.of(2025, 1, 1, 0, 0), null);
            assertThat(services.reports.totalRequests()).isEqualTo(5);
            assertThat(services.reports.totalReaders()).isEqualTo(3);
            assertThat(services.reports.activeRequests()).isEqualTo(2);
            assertThat(services.reports.overdueRequests()).isEqualTo(1);
            assertThat(services.reports.returnedRequests()).isEqualTo(1);
            assertThat(services.reports.cancelledOrRejected()).isEqualTo(2);
            assertThat(services.reports.requestsLast30Days()).isEqualTo(1);
            assertThat(services.reports.averageRequestsPerReader()).isCloseTo(5.0 / 3, org.assertj.core.data.Offset.offset(0.00001));
            assertThat(services.reports.countGroupedByStatus()).containsEntry("APPROVED", 0L).containsEntry("ISSUED", 1L);
            assertThat(services.reports.topBooks(1)).containsExactly(new ReportService.NameCount("A", 2));
            assertThat(services.reports.topAuthors(1)).containsExactly(new ReportService.NameCount("X", 2));
            assertThat(services.reports.topReaders(1)).containsExactly(new ReportService.ReaderActivity(two.getId(), two.getFullName(), 3));
            assertThat(services.reports.readerActivity()).hasSize(3);
            assertThat(services.reports.readerActivity()).extracting(ReportService.ReaderActivity::readerId)
                    .containsExactlyInAnyOrder(one.getId(), two.getId(), idle.getId());
            assertThat(services.reports.readersWithoutRequests()).extracting(Reader::getId).containsExactly(idle.getId());
            assertThat(services.reports.requestsByMonth(2024)).containsEntry(1, 1L).containsEntry(12, 1L).containsEntry(2, 0L);
            assertThat(services.reports.requestsByMonth(2024).values().stream().mapToLong(Long::longValue).sum()).isEqualTo(2);
            assertThat(services.reports.requestsForExport()).hasSize(5);
            assertThat(services.reports.requestsForExport().getFirst().getReaderName()).isEqualTo(one.getFullName());
            assertThat(services.reports.requestsForExport().getFirst().getStatus()).isEqualTo(RequestStatus.CREATED);
        });
    }

    @Test
    void rollsBackAllServiceWritesWhenOuterTransactionFails() {
        withServices(services -> {
            assertThat(AopUtils.isAopProxy(services.readers)).isTrue();
            assertThat(AopUtils.isAopProxy(services.requests)).isTrue();
            TransactionTemplate transaction = new TransactionTemplate(services.transactionManager);
            assertThatThrownBy(() -> transaction.execute(status -> {
                Reader reader = reader(services, 1);
                request(services, reader, "Откатить вместе с читателем");
                throw new BusinessException("Откат операции");
            })).isInstanceOf(BusinessException.class);
            assertThat(services.readers.getAll()).isEmpty();
            assertThat(services.requests.getAll()).isEmpty();
        });
    }

    private Reader reader(Services services, int number) {
        return services.readers.create("Читатель " + number, "reader" + number + "@example.org", null, "LIB-000" + number);
    }

    private BookRequest request(Services services, Reader reader, String title) {
        return services.requests.create(reader.getId(), title, "Автор", null, null, null);
    }

    private void seedRequest(Reader reader, String title, String author, RequestStatus status,
                             LocalDateTime createdAt, LocalDate due) {
        jdbc.update("INSERT INTO book_requests(reader_id, book_title, book_author, status, created_at, desired_return_date) "
                + "VALUES (?, ?, ?, ?, ?, ?)", reader.getId(), title, author, status.name(), createdAt, due);
    }

    private void withServices(Consumer<Services> action) {
        String schemaUrl = url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema;
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(ServiceConfiguration.class)
                .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class,
                        FlywayAutoConfiguration.class, HibernateJpaAutoConfiguration.class, TransactionAutoConfiguration.class))
                .withPropertyValues("spring.datasource.url=" + schemaUrl,
                        "spring.datasource.username=" + username, "spring.datasource.password=" + password,
                        "spring.flyway.schemas=" + schema, "spring.flyway.default-schema=" + schema,
                        "spring.jpa.properties.hibernate.default_schema=" + schema)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    action.accept(new Services(context.getBean(ReaderService.class), context.getBean(BookRequestService.class),
                            context.getBean(ReportService.class), context.getBean(PlatformTransactionManager.class)));
                });
    }

    private record Services(ReaderService readers, BookRequestService requests, ReportService reports,
                            PlatformTransactionManager transactionManager) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = Reader.class)
    @EnableJpaRepositories(basePackageClasses = ReaderRepository.class)
    @Import({ReaderService.class, BookRequestService.class, ReportService.class, ru.mirea.library.service.AuditService.class})
    static class ServiceConfiguration {
    }
}
