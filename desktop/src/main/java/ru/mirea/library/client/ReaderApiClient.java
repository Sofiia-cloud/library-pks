package ru.mirea.library.client;

import ru.mirea.library.dto.*;

import java.util.*;

public class ReaderApiClient {
    private final ApiClient api;

    public ReaderApiClient(ApiClient api) {
        this.api = api;
    }

    public List<ReaderDto> list() {
        return api.getList("/api/readers", ReaderDto.class);
    }

    public ReaderDto get(UUID id) {
        return api.get("/api/readers/" + id, ReaderDto.class);
    }

    public ReaderDto create(ReaderCreate body) {
        return api.post("/api/readers", body, ReaderDto.class);
    }

    public ReaderDto update(UUID id, ReaderUpdate body) {
        return api.put("/api/readers/" + id, body, ReaderDto.class);
    }

    public void delete(ReaderDto reader) {
        api.delete("/api/readers/" + reader.id() + "?version=" + reader.version());
    }
}
