package ru.mirea.library.service;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.mirea.library.domain.BookRequest;
import ru.mirea.library.domain.Reader;
import ru.mirea.library.error.BusinessException;
import ru.mirea.library.error.EntityNotFoundException;
import ru.mirea.library.filter.BookRequestSpecifications;
import ru.mirea.library.model.RequestStatus;
import ru.mirea.library.repository.BookRequestRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class BookRequestService {
    private static final Sort BY_NUMBER = Sort.by("requestNumber");
    private final BookRequestRepository repository;
    private final ReaderService readers;
    private final AuditService audit;
    private final ru.mirea.library.repository.TagRepository tags;

    public BookRequestService(
            BookRequestRepository repository,
            ReaderService readers,
            AuditService audit,
            ru.mirea.library.repository.TagRepository tags) {
        this.repository = repository;
        this.readers = readers;
        this.audit = audit;
        this.tags = tags;
    }

    @Transactional
    public BookRequest create(
            UUID readerId,
            String title,
            String author,
            String isbn,
            LocalDate due,
            String comment) {
        return create(
                new ru.mirea.library.dto.BookRequestCreate(
                        readerId, title, author, isbn, due, comment, RequestStatus.CREATED));
    }

    @Transactional
    public BookRequest create(
            UUID readerId,
            String title,
            String author,
            String isbn,
            LocalDate due,
            String comment,
            RequestStatus status) {
        return create(
                new ru.mirea.library.dto.BookRequestCreate(
                        readerId, title, author, isbn, due, comment, status));
    }

    public List<BookRequest> list(
            String text,
            ru.mirea.library.model.SearchField field,
            UUID readerId,
            RequestStatus status,
            LocalDateTime from,
            LocalDateTime to,
            ru.mirea.library.model.RequestSort sort,
            boolean ascending) {
        Specification<BookRequest> conditions = BookRequestSpecifications.all();
        if (text != null && !text.isBlank()) {
            String query = searchText(text);
            conditions =
                    conditions.and(
                            switch (field) {
                                case TITLE -> BookRequestSpecifications.titleContains(query);
                                case AUTHOR -> BookRequestSpecifications.authorContains(query);
                                case READER_NAME ->
                                        BookRequestSpecifications.readerNameContains(query);
                                case CARD -> BookRequestSpecifications.readerCardEquals(query);
                            });
        }
        if (readerId != null)
            conditions = conditions.and(BookRequestSpecifications.readerEquals(readerId));
        if (status != null)
            conditions = conditions.and(BookRequestSpecifications.statusEquals(status));
        if (from != null || to != null) {
            validateDateRange(from, to);
            conditions = conditions.and(BookRequestSpecifications.createdBetween(from, to));
        }
        if (sort == ru.mirea.library.model.RequestSort.STATUS) {
            return select(
                    conditions.and(BookRequestSpecifications.orderByStatus(ascending)),
                    Sort.unsorted());
        }
        String property =
                switch (sort) {
                    case TITLE -> "bookTitle";
                    case CREATED_AT -> "createdAt";
                    default -> "requestNumber";
                };
        Sort.Order order =
                new Sort.Order(ascending ? Sort.Direction.ASC : Sort.Direction.DESC, property);
        if (sort == ru.mirea.library.model.RequestSort.TITLE) order = order.ignoreCase();
        return select(conditions, Sort.by(order).and(BY_NUMBER));
    }

    public BookRequest getById(UUID id) {
        if (id == null) throw new BusinessException("UUID заявки обязателен");
        return repository
                .findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Заявка не найдена: " + id));
    }

    public List<BookRequest> getAll() {
        return select(BookRequestSpecifications.all(), BY_NUMBER);
    }

    @Transactional
    public BookRequest update(
            UUID id,
            long version,
            String title,
            String author,
            String isbn,
            LocalDate due,
            String comment,
            RequestStatus status) {
        var r = updateFields(id, version, title, author, isbn, due, comment, status);
        repository.saveAndFlush(r);
        audit.record("UPDATE", "REQUEST", id, "Изменена заявка");
        return r;
    }

    private BookRequest updateFields(
            UUID id,
            long version,
            String bookTitle,
            String bookAuthor,
            String isbn,
            LocalDate desiredReturnDate,
            String comment,
            RequestStatus status) {
        BookRequest request = getById(id);
        checkVersion(request, version);
        requireActive(request);
        String title = required(bookTitle, "Название книги", 200);
        String author = required(bookAuthor, "Автор", 150);
        String normalizedIsbn = optional(isbn, "ISBN", 20);
        if (!Objects.equals(desiredReturnDate, request.getDesiredReturnDate()))
            validateNewReturnDate(desiredReturnDate);
        validateTransition(request.getStatus(), status);
        request.setBookTitle(title);
        request.setBookAuthor(author);
        request.setIsbn(normalizedIsbn);
        request.setDesiredReturnDate(desiredReturnDate);
        request.setComment(optional(comment, "Комментарий", 4000));
        request.setStatus(status);
        return request;
    }

    @Transactional
    public BookRequest changeStatus(UUID id, long version, RequestStatus status) {
        BookRequest request = getById(id);
        checkVersion(request, version);
        requireActive(request);
        validateTransition(request.getStatus(), status);
        request.setStatus(status);
        repository.saveAndFlush(request);
        audit.record("STATUS", "REQUEST", id, "Изменён статус: " + status);
        return request;
    }

    @Transactional
    public BookRequest delete(UUID id, long version) {
        BookRequest request = getById(id);
        checkVersion(request, version);
        if (request.getStatus() == RequestStatus.ISSUED) {
            throw new BusinessException("Сначала оформите возврат выданной книги");
        }
        request.setArchived(true);
        repository.saveAndFlush(request);
        audit.record("ARCHIVE", "REQUEST", id, "Заявка в архиве");
        return request;
    }

    @Transactional
    public BookRequest restore(UUID id, long version) {
        BookRequest r = getById(id);
        checkVersion(r, version);
        r.setArchived(false);
        repository.saveAndFlush(r);
        audit.record("RESTORE", "REQUEST", id, "Заявка восстановлена");
        return r;
    }

    @Transactional
    public void permanent(UUID id, long version) {
        BookRequest r = getById(id);
        checkVersion(r, version);
        if (!r.isArchived()) throw new BusinessException("Сначала переместите заявку в архив");
        repository.delete(r);
        repository.flush();
        audit.record("DELETE", "REQUEST", id, "Заявка удалена окончательно");
    }

    @Transactional
    public BookRequest create(ru.mirea.library.dto.BookRequestCreate b) {
        String title = required(b.bookTitle(), "Название книги", 200),
                author = required(b.bookAuthor(), "Автор", 150),
                isbn = optional(b.isbn(), "ISBN", 20),
                comment = optional(b.comment(), "Комментарий", 4000);
        validateNewReturnDate(b.desiredReturnDate());
        validateTransition(RequestStatus.CREATED, b.status());
        Reader reader = readers.getById(b.readerId());
        if (!reader.isActive())
            throw new BusinessException("Нельзя создать заявку для отключённого читателя");
        var resolved = resolveTags(b.tags());
        var r = new BookRequest(reader, title, author, isbn, b.desiredReturnDate(), comment);
        r.setStatus(b.status());
        if (b.externalCode() != null) {
            String code = b.externalCode().trim();
            if (!code.matches("[A-Za-z0-9._-]{1,64}"))
                throw new BusinessException("Некорректный внешний код");
            r.setExternalCode(code);
        }
        metadata(r, b.requestedCopies(), b.urgent(), resolved);
        repository.saveAndFlush(r);
        audit.record("CREATE", "REQUEST", r.getId(), "Создана заявка");
        return r;
    }

    @Transactional
    public BookRequest update(UUID id, ru.mirea.library.dto.BookRequestUpdate b) {
        var resolved = resolveTags(b.tags());
        var r =
                updateFields(
                        id,
                        b.version(),
                        b.bookTitle(),
                        b.bookAuthor(),
                        b.isbn(),
                        b.desiredReturnDate(),
                        b.comment(),
                        b.status());
        metadata(r, b.requestedCopies(), b.urgent(), resolved);
        repository.saveAndFlush(r);
        audit.record("UPDATE", "REQUEST", id, "Изменена заявка");
        return r;
    }

    private void metadata(
            BookRequest r,
            int copies,
            boolean urgent,
            java.util.Set<ru.mirea.library.domain.Tag> values) {
        if (copies < 1 || copies > 1000) throw new BusinessException("Количество от 1 до 1000");
        r.setRequestedCopies(copies);
        r.setUrgent(urgent);
        r.setTags(values);
    }

    public static java.util.Set<String> normalizeTags(java.util.Set<String> names) {
        var result = new java.util.TreeSet<String>();
        if (names == null) return result;
        for (String raw : names) {
            if (raw == null) throw new BusinessException("Пустой тег");
            String n = raw.trim().toLowerCase(java.util.Locale.ROOT);
            if (n.isBlank()
                    || n.length() > 60
                    || n.contains("|")
                    || n.contains("\n")
                    || n.contains("\r"))
                throw new BusinessException("Тег: 1–60 символов, без | и переноса строки");
            result.add(n);
        }
        if (result.size() > 20) throw new BusinessException("Не более 20 тегов");
        return result;
    }

    private java.util.Set<ru.mirea.library.domain.Tag> resolveTags(java.util.Set<String> names) {
        var result = new java.util.HashSet<ru.mirea.library.domain.Tag>();
        for (String n : normalizeTags(names)) {
            tags.insertIfMissing(n);
            result.add(tags.findById(n).orElseThrow());
        }
        return result;
    }

    @Transactional
    public BookRequest changeTag(UUID id, long version, String tag, boolean add) {
        BookRequest r = getById(id);
        checkVersion(r, version);
        requireActive(r);
        var names =
                r.getTags().stream()
                        .map(ru.mirea.library.domain.Tag::getName)
                        .collect(java.util.stream.Collectors.toSet());
        if (add) names.add(tag);
        else names.remove(normalizeTags(java.util.Set.of(tag)).iterator().next());
        r.setTags(resolveTags(names));
        repository.saveAndFlush(r);
        audit.record("TAGS", "REQUEST", id, "Изменены теги");
        return r;
    }

    private static void requireActive(BookRequest r) {
        if (r.isArchived()) throw new BusinessException("Сначала восстановите заявку из архива");
    }

    public List<BookRequest> searchByTitle(String text) {
        return select(BookRequestSpecifications.titleContains(searchText(text)), BY_NUMBER);
    }

    public List<BookRequest> searchByAuthor(String text) {
        return select(BookRequestSpecifications.authorContains(searchText(text)), BY_NUMBER);
    }

    public List<BookRequest> searchByReaderName(String text) {
        return select(BookRequestSpecifications.readerNameContains(searchText(text)), BY_NUMBER);
    }

    public List<BookRequest> findByReaderCardNumber(String card) {
        return select(
                BookRequestSpecifications.readerCardEquals(required(card, "Номер билета", 30)),
                BY_NUMBER);
    }

    public List<BookRequest> findByStatus(RequestStatus status) {
        if (status == null) throw new BusinessException("Статус не указан");
        return select(BookRequestSpecifications.statusEquals(status), BY_NUMBER);
    }

    public List<BookRequest> findByReaderId(UUID readerId) {
        if (readerId == null) throw new BusinessException("UUID читателя обязателен");
        return select(BookRequestSpecifications.readerEquals(readerId), BY_NUMBER);
    }

    public List<BookRequest> findByDateRange(LocalDateTime from, LocalDateTime to) {
        validateDateRange(from, to);
        return select(
                BookRequestSpecifications.createdBetween(from, to),
                Sort.by("createdAt").and(BY_NUMBER));
    }

    public List<BookRequest> filter(
            UUID readerId, RequestStatus status, LocalDateTime from, LocalDateTime to) {
        Specification<BookRequest> conditions = BookRequestSpecifications.all();
        if (readerId != null)
            conditions = conditions.and(BookRequestSpecifications.readerEquals(readerId));
        if (status != null)
            conditions = conditions.and(BookRequestSpecifications.statusEquals(status));
        if (from != null || to != null) {
            validateDateRange(from, to);
            conditions = conditions.and(BookRequestSpecifications.createdBetween(from, to));
        }
        return select(conditions, BY_NUMBER);
    }

    public List<BookRequest> sortByCreatedAt(boolean ascending) {
        return select(
                BookRequestSpecifications.all(),
                Sort.by(ascending ? Sort.Direction.ASC : Sort.Direction.DESC, "createdAt")
                        .and(BY_NUMBER));
    }

    public List<BookRequest> sortByBookTitle(boolean ascending) {
        Sort.Order titleOrder =
                new Sort.Order(ascending ? Sort.Direction.ASC : Sort.Direction.DESC, "bookTitle")
                        .ignoreCase();
        return select(BookRequestSpecifications.all(), Sort.by(titleOrder).and(BY_NUMBER));
    }

    public List<BookRequest> sortByStatus(boolean ascending) {
        return repository.findAllOrderedByStatus(ascending ? 1 : -1);
    }

    private List<BookRequest> select(Specification<BookRequest> conditions, Sort sort) {
        return repository.findAll(conditions, sort);
    }

    private static void checkVersion(BookRequest request, long expected) {
        if (expected < 0) throw new BusinessException("Версия не может быть отрицательной");
        if (request.getVersion() != expected)
            throw new ObjectOptimisticLockingFailureException(BookRequest.class, request.getId());
    }

    private static void validateTransition(RequestStatus current, RequestStatus next) {
        if (next == null) throw new BusinessException("Статус не указан");
        if (current != next && !current.canTransitionTo(next)) {
            throw new BusinessException(
                    "Недопустимый переход статуса: "
                            + current.getRussianName()
                            + " → "
                            + next.getRussianName());
        }
    }

    private static void validateNewReturnDate(LocalDate date) {
        if (date != null && date.isBefore(LocalDate.now()))
            throw new BusinessException("Дата возврата не может быть в прошлом");
    }

    private static void validateDateRange(LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null) throw new BusinessException("Обе даты обязательны");
        if (from.isAfter(to)) throw new BusinessException("Начало диапазона позже конца");
    }

    private static String searchText(String text) {
        if (text == null) throw new BusinessException("Поисковая строка не указана");
        return text.trim();
    }

    private static String required(String text, String field, int maxLength) {
        if (text == null || text.isBlank())
            throw new BusinessException(field + ": обязательное поле");
        String value = text.trim();
        if (value.length() > maxLength)
            throw new BusinessException(field + ": не более " + maxLength + " символов");
        return value;
    }

    private static String optional(String text, String field, int maxLength) {
        String value = optionalText(text);
        if (value != null && value.length() > maxLength)
            throw new BusinessException(field + ": не более " + maxLength + " символов");
        return value;
    }

    private static String optionalText(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }
}
