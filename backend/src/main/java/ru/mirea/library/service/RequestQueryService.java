package ru.mirea.library.service;

import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import ru.mirea.library.dto.*;
import ru.mirea.library.filter.RequestFilter;
import ru.mirea.library.model.*;
import ru.mirea.library.repository.*;

import java.util.*;

@Service
@Transactional(readOnly = true)
public class RequestQueryService {
    private final BookRequestRepository repository;

    public RequestQueryService(BookRequestRepository repository) {
        this.repository = repository;
    }

    public PageDto<BookRequestDto> list(RequestFilter f, int page, int size) {
        Paging.check(page, size);
        return Paging.dto(
                repository
                        .findAll(f.specification(), PageRequest.of(page, size, f.ordering()))
                        .map(DtoMapper::request));
    }

    public List<BookRequestDto> all(RequestFilter f) {
        return repository.findAll(f.specification(), f.ordering()).stream()
                .map(DtoMapper::request)
                .toList();
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AnalyticsDto summary(RequestFilter f) {
        var rows = repository.findAll(f.specification(), Sort.unsorted());
        long total = rows.size(),
                archived = rows.stream().filter(b -> b.isArchived()).count(),
                urgent = rows.stream().filter(b -> b.isUrgent()).count();
        var statuses = new EnumMap<RequestStatus, Long>(RequestStatus.class);
        for (var s : RequestStatus.values()) statuses.put(s, 0L);
        var states = new EnumMap<ReturnState, Long>(ReturnState.class);
        for (var s : ReturnState.values()) states.put(s, 0L);
        var readers = new LinkedHashMap<UUID, Long>();
        var names = new HashMap<UUID, String>();
        for (var b : rows) {
            statuses.merge(b.getStatus(), 1L, Long::sum);
            states.merge(
                    ReturnState.calculate(
                            b.getStatus(), b.getDesiredReturnDate(), java.time.LocalDate.now()),
                    1L,
                    Long::sum);
            readers.merge(b.getReader().getId(), 1L, Long::sum);
            names.put(
                    b.getReader().getId(),
                    b.getReader().getFullName()
                            + " ("
                            + b.getReader().getLibraryCardNumber()
                            + ")");
        }
        return new AnalyticsDto(
                total,
                total - archived,
                archived,
                urgent,
                rows.stream().mapToInt(b -> b.getRequestedCopies()).average().orElse(0),
                readers.entrySet().stream()
                        .map(e -> new NameCountDto(names.get(e.getKey()), e.getValue()))
                        .toList(),
                statuses,
                states);
    }
}
