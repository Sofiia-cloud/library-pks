package ru.mirea.library.service;

import ru.mirea.library.exception.BusinessException;
import ru.mirea.library.model.BookRequest;
import ru.mirea.library.model.RequestStatus;
import ru.mirea.library.repository.BookRequestRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Фильтрация заявок.
 */
public class FilterService {

    private final BookRequestRepository repo;

    public FilterService(BookRequestRepository repo) {
        this.repo = repo;
    }

    public List<BookRequest> byStatus(RequestStatus s) {
        if (s == null) throw new BusinessException("Статус не указан");
        return repo.findByStatus(s);
    }

    public List<BookRequest> byReader(int readerId) {
        if (readerId <= 0) throw new BusinessException("ID читателя должен быть > 0");
        return repo.findByReaderId(readerId);
    }

    public List<BookRequest> byDateRange(LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null) throw new BusinessException("Обе даты обязательны");
        if (from.isAfter(to)) throw new BusinessException("Начало диапазона позже конца");
        return repo.findByDateRange(from, to);
    }
}