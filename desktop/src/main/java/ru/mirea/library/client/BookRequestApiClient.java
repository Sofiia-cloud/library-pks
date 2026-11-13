package ru.mirea.library.client;

import ru.mirea.library.dto.*;

import java.util.*;

public class BookRequestApiClient {
    private final ApiClient api;

    public BookRequestApiClient(ApiClient api) {
        this.api = api;
    }

    public List<BookRequestDto> list() {
        return list(Map.of());
    }

    public List<BookRequestDto> list(Map<String, ?> filters) {
        var all = new ArrayList<BookRequestDto>();
        var f = new LinkedHashMap<String, Object>(filters);
        f.put("size", 100);
        for (int page = 0; ; page++) {
            f.put("page", page);
            var result = page(f);
            all.addAll(result.items());
            if (page + 1 >= result.totalPages()) return all;
        }
    }

    public PageDto<BookRequestDto> page(Map<String, ?> filters) {
        return api.get(
                "/api/book-requests" + ApiClient.query(filters),
                new com.fasterxml.jackson.core.type.TypeReference<PageDto<BookRequestDto>>() {});
    }

    public BookRequestDto get(UUID id) {
        return api.get("/api/book-requests/" + id, BookRequestDto.class);
    }

    public BookRequestDto create(BookRequestCreate body) {
        return api.post("/api/book-requests", body, BookRequestDto.class);
    }

    public BookRequestDto update(UUID id, BookRequestUpdate body) {
        return api.put("/api/book-requests/" + id, body, BookRequestDto.class);
    }

    public BookRequestDto status(
            BookRequestDto request, ru.mirea.library.model.RequestStatus status) {
        return api.put(
                "/api/book-requests/" + request.id() + "/status",
                new StatusUpdate(request.version(), status),
                BookRequestDto.class);
    }

    public void delete(BookRequestDto request) {
        api.delete("/api/book-requests/" + request.id() + "?version=" + request.version());
    }
}
