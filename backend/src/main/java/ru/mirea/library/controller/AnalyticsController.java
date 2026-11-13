package ru.mirea.library.controller;

import org.springframework.web.bind.annotation.*;

import ru.mirea.library.dto.AnalyticsDto;
import ru.mirea.library.filter.RequestFilter;
import ru.mirea.library.model.ArchiveScope;
import ru.mirea.library.service.RequestQueryService;

@RestController
public class AnalyticsController {
    private final RequestQueryService query;

    public AnalyticsController(RequestQueryService query) {
        this.query = query;
    }

    @GetMapping("/api/analytics/summary")
    public AnalyticsDto summary(
            @ModelAttribute RequestFilter f,
            @RequestParam(value = "scope", defaultValue = "ALL") ArchiveScope scope) {
        f.setScope(scope);
        return query.summary(f);
    }
}
