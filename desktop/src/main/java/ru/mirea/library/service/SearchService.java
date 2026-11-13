package ru.mirea.library.service;

import ru.mirea.library.model.BookRequest;
import ru.mirea.library.repository.BookRequestRepository;

import java.util.List;

/**
 * Поиск заявок — обёртка над уже существующими SQL-методами репозитория.
 */
public class SearchService {

    private final BookRequestRepository repo;

    public SearchService(BookRequestRepository repo) {
        this.repo = repo;
    }

    public List<BookRequest> byTitle(String q)      { return repo.searchByTitle(q); }
    public List<BookRequest> byAuthor(String q)     { return repo.searchByAuthor(q); }
    public List<BookRequest> byReaderName(String q) { return repo.searchByReaderName(q); }
    public List<BookRequest> byReaderCard(String q) { return repo.findByReaderCardNumber(q); }
}