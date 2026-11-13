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

    public BookRequestService(BookRequestRepository repository, ReaderService readers) {
        this.repository = repository;
        this.readers = readers;
    }

    @Transactional
    public BookRequest create(UUID readerId, String bookTitle, String bookAuthor, String isbn,
                              LocalDate desiredReturnDate, String comment) {
        String title = required(bookTitle, "Название книги", 200);
        String author = required(bookAuthor, "Автор", 150);
        String normalizedIsbn = optional(isbn, "ISBN", 20);
        validateNewReturnDate(desiredReturnDate);
        Reader reader = readers.getById(readerId);
        if (!reader.isActive()) throw new BusinessException("Нельзя создать заявку для отключённого читателя");
        BookRequest request = new BookRequest(reader, title, author, normalizedIsbn, desiredReturnDate, optionalText(comment));
        return repository.saveAndFlush(request);
    }

    public BookRequest getById(UUID id) {
        if (id == null) throw new BusinessException("UUID заявки обязателен");
        return repository.findById(id).orElseThrow(() -> new EntityNotFoundException("Заявка не найдена: " + id));
    }

    public List<BookRequest> getAll() {
        return select(BookRequestSpecifications.all(), BY_NUMBER);
    }

    @Transactional
    public BookRequest update(UUID id, long version, String bookTitle, String bookAuthor, String isbn,
                              LocalDate desiredReturnDate, String comment, RequestStatus status) {
        BookRequest request = getById(id);
        checkVersion(request, version);
        String title = required(bookTitle, "Название книги", 200);
        String author = required(bookAuthor, "Автор", 150);
        String normalizedIsbn = optional(isbn, "ISBN", 20);
        if (!Objects.equals(desiredReturnDate, request.getDesiredReturnDate())) validateNewReturnDate(desiredReturnDate);
        validateTransition(request.getStatus(), status);
        request.setBookTitle(title);
        request.setBookAuthor(author);
        request.setIsbn(normalizedIsbn);
        request.setDesiredReturnDate(desiredReturnDate);
        request.setComment(optionalText(comment));
        request.setStatus(status);
        return repository.saveAndFlush(request);
    }

    @Transactional
    public BookRequest changeStatus(UUID id, long version, RequestStatus status) {
        BookRequest request = getById(id);
        checkVersion(request, version);
        validateTransition(request.getStatus(), status);
        request.setStatus(status);
        return repository.saveAndFlush(request);
    }

    @Transactional
    public void delete(UUID id, long version) {
        BookRequest request = getById(id);
        checkVersion(request, version);
        if (request.getStatus() == RequestStatus.ISSUED) {
            throw new BusinessException("Нельзя удалить заявку со статусом ISSUED");
        }
        repository.delete(request);
        repository.flush();
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
        return select(BookRequestSpecifications.readerCardEquals(required(card, "Номер билета", 30)), BY_NUMBER);
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
        return select(BookRequestSpecifications.createdBetween(from, to), Sort.by("createdAt").and(BY_NUMBER));
    }

    public List<BookRequest> filter(UUID readerId, RequestStatus status, LocalDateTime from, LocalDateTime to) {
        Specification<BookRequest> conditions = BookRequestSpecifications.all();
        if (readerId != null) conditions = conditions.and(BookRequestSpecifications.readerEquals(readerId));
        if (status != null) conditions = conditions.and(BookRequestSpecifications.statusEquals(status));
        if (from != null || to != null) {
            validateDateRange(from, to);
            conditions = conditions.and(BookRequestSpecifications.createdBetween(from, to));
        }
        return select(conditions, BY_NUMBER);
    }

    public List<BookRequest> sortByCreatedAt(boolean ascending) {
        return select(BookRequestSpecifications.all(), Sort.by(ascending ? Sort.Direction.ASC : Sort.Direction.DESC,
                "createdAt").and(BY_NUMBER));
    }

    public List<BookRequest> sortByBookTitle(boolean ascending) {
        Sort.Order titleOrder = new Sort.Order(ascending ? Sort.Direction.ASC : Sort.Direction.DESC, "bookTitle").ignoreCase();
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
        if (request.getVersion() != expected) throw new ObjectOptimisticLockingFailureException(BookRequest.class, request.getId());
    }

    private static void validateTransition(RequestStatus current, RequestStatus next) {
        if (next == null) throw new BusinessException("Статус не указан");
        if (current != next && !current.canTransitionTo(next)) {
            throw new BusinessException("Недопустимый переход статуса: " + current.getRussianName() + " → " + next.getRussianName());
        }
    }

    private static void validateNewReturnDate(LocalDate date) {
        if (date != null && date.isBefore(LocalDate.now())) throw new BusinessException("Дата возврата не может быть в прошлом");
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
        if (text == null || text.isBlank()) throw new BusinessException(field + ": обязательное поле");
        String value = text.trim();
        if (value.length() > maxLength) throw new BusinessException(field + ": не более " + maxLength + " символов");
        return value;
    }

    private static String optional(String text, String field, int maxLength) {
        String value = optionalText(text);
        if (value != null && value.length() > maxLength) throw new BusinessException(field + ": не более " + maxLength + " символов");
        return value;
    }

    private static String optionalText(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }
}
