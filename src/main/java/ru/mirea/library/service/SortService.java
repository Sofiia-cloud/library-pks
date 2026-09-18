package ru.mirea.library.service;

import ru.mirea.library.model.BookRequest;
import ru.mirea.library.repository.BookRequestRepository;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Сортировки через Stream API (демонстрация требования КР).
 */
public class SortService {

    private final BookRequestRepository repo;

    public SortService(BookRequestRepository repo) {
        this.repo = repo;
    }

    public List<BookRequest> byCreatedAt(boolean asc) {
        Comparator<BookRequest> cmp = Comparator.comparing(BookRequest::getCreatedAt);
        return repo.findAll().stream()
                .sorted(asc ? cmp : cmp.reversed())
                .collect(Collectors.toList());
    }

    public List<BookRequest> byBookTitle(boolean asc) {
        Comparator<BookRequest> cmp = Comparator.comparing(
                BookRequest::getBookTitle, String.CASE_INSENSITIVE_ORDER);
        return repo.findAll().stream()
                .sorted(asc ? cmp : cmp.reversed())
                .collect(Collectors.toList());
    }

    public List<BookRequest> byStatus(boolean asc) {
        Comparator<BookRequest> cmp = Comparator.comparing(b -> b.getStatus().ordinal());
        return repo.findAll().stream()
                .sorted(asc ? cmp : cmp.reversed())
                .collect(Collectors.toList());
    }
}