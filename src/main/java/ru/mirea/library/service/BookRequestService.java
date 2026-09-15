package ru.mirea.library.service;

import ru.mirea.library.exception.BusinessException;
import ru.mirea.library.exception.EntityNotFoundException;
import ru.mirea.library.model.BookRequest;
import ru.mirea.library.model.RequestStatus;
import ru.mirea.library.repository.BookRequestRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class BookRequestService {

    private final BookRequestRepository repository;
    private final ReaderService readerService;

    public BookRequestService(BookRequestRepository repository, ReaderService readerService) {
        this.repository = repository;
        this.readerService = readerService;
    }

    public BookRequest create(int readerId,
                              String bookTitle,
                              String bookAuthor,
                              String isbn,
                              LocalDate desiredReturnDate,
                              String comment) {
        if (bookTitle == null || bookTitle.isBlank()) {
            throw new BusinessException("Название книги обязательно");
        }
        if (bookAuthor == null || bookAuthor.isBlank()) {
            throw new BusinessException("Автор книги обязателен");
        }
        if (!readerService.existsById(readerId)) {
            throw new BusinessException("Читатель с ID=" + readerId + " не найден");
        }
        if (desiredReturnDate != null && desiredReturnDate.isBefore(LocalDate.now())) {
            throw new BusinessException("Дата возврата не может быть в прошлом");
        }

        BookRequest request = new BookRequest(
            readerId,
            bookTitle.trim(),
            bookAuthor.trim(),
            isbn == null || isbn.isBlank() ? null : isbn.trim(),
            desiredReturnDate,
            comment == null || comment.isBlank() ? null : comment.trim()
        );
        return repository.save(request);
    }

    public BookRequest getById(int id) {
        return repository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException(
                "Заявка с ID=" + id + " не найдена"));
    }

    public List<BookRequest> getAll() {
        return repository.findAll();
    }

    public BookRequest update(int id,
                              String bookTitle,
                              String bookAuthor,
                              String isbn,
                              LocalDate desiredReturnDate,
                              String comment) {
        BookRequest existing = getById(id);

        if (bookTitle == null || bookTitle.isBlank()) {
            throw new BusinessException("Название книги обязательно");
        }
        if (bookAuthor == null || bookAuthor.isBlank()) {
            throw new BusinessException("Автор книги обязателен");
        }
        if (desiredReturnDate != null && desiredReturnDate.isBefore(LocalDate.now())) {
            throw new BusinessException("Дата возврата не может быть в прошлом");
        }

        existing.setBookTitle(bookTitle.trim());
        existing.setBookAuthor(bookAuthor.trim());
        existing.setIsbn(isbn == null || isbn.isBlank() ? null : isbn.trim());
        existing.setDesiredReturnDate(desiredReturnDate);
        existing.setComment(comment == null || comment.isBlank() ? null : comment.trim());

        if (!repository.update(existing)) {
            throw new BusinessException("Не удалось обновить заявку");
        }
        return existing;
    }

    public void delete(int id) {
        BookRequest existing = getById(id);
        if (existing.getStatus() == RequestStatus.ISSUED) {
            throw new BusinessException("Нельзя удалить заявку со статусом ISSUED");
        }
        if (!repository.deleteById(id)) {
            throw new BusinessException("Не удалось удалить заявку с ID=" + id);
        }
    }

    public BookRequest changeStatus(int id, RequestStatus newStatus) {
        BookRequest request = getById(id);
        if (!request.getStatus().canTransitionTo(newStatus)) {
            throw new BusinessException(String.format(
                "Недопустимый переход статуса: %s → %s",
                request.getStatus().getRussianName(),
                newStatus.getRussianName()));
        }
        request.setStatus(newStatus);
        if (!repository.update(request)) {
            throw new BusinessException("Не удалось обновить статус заявки");
        }
        return request;
    }

    public List<BookRequest> findByStatus(RequestStatus status) {
        return repository.findByStatus(status);
    }

    public List<BookRequest> findByReaderId(int readerId) {
        return repository.findByReaderId(readerId);
    }

    public List<BookRequest> searchByTitle(String query) {
        return repository.searchByTitle(query);
    }

    public List<BookRequest> searchByAuthor(String query) {
        return repository.searchByAuthor(query);
    }

    public List<BookRequest> findByDateRange(LocalDateTime from, LocalDateTime to) {
        return repository.findByDateRange(from, to);
    }
}