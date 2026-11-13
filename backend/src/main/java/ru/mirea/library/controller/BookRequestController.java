package ru.mirea.library.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import ru.mirea.library.dto.*;
import ru.mirea.library.model.*;
import ru.mirea.library.service.BookRequestService;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/book-requests")
@Validated
public class BookRequestController {
    private final BookRequestService service;

    public BookRequestController(BookRequestService service) {
        this.service = service;
    }

    @GetMapping
    public List<BookRequestDto> list(
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "field", defaultValue = "TITLE") SearchField field,
            @RequestParam(value = "readerId", required = false) UUID readerId,
            @RequestParam(value = "status", required = false) RequestStatus status,
            @RequestParam(value = "from", required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime from,
            @RequestParam(value = "to", required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime to,
            @RequestParam(value = "sort", defaultValue = "NUMBER") RequestSort sort,
            @RequestParam(value = "ascending", defaultValue = "true") boolean ascending) {
        return service.list(q, field, readerId, status, from, to, sort, ascending).stream()
                .map(DtoMapper::request)
                .toList();
    }

    @GetMapping("/{id}")
    public BookRequestDto get(@PathVariable("id") UUID id) {
        return DtoMapper.request(service.getById(id));
    }

    @PostMapping
    public ResponseEntity<BookRequestDto> create(@Valid @RequestBody BookRequestCreate body) {
        var request =
                service.create(
                        body.readerId(),
                        body.bookTitle(),
                        body.bookAuthor(),
                        body.isbn(),
                        body.desiredReturnDate(),
                        body.comment(),
                        body.status());
        return ResponseEntity.created(URI.create("/api/book-requests/" + request.getId()))
                .body(DtoMapper.request(request));
    }

    @PutMapping("/{id}")
    public BookRequestDto update(
            @PathVariable("id") UUID id, @Valid @RequestBody BookRequestUpdate body) {
        return DtoMapper.request(
                service.update(
                        id,
                        body.version(),
                        body.bookTitle(),
                        body.bookAuthor(),
                        body.isbn(),
                        body.desiredReturnDate(),
                        body.comment(),
                        body.status()));
    }

    @PutMapping("/{id}/status")
    public BookRequestDto status(
            @PathVariable("id") UUID id, @Valid @RequestBody StatusUpdate body) {
        return DtoMapper.request(service.changeStatus(id, body.version(), body.status()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable("id") UUID id, @RequestParam("version") @PositiveOrZero long version) {
        service.delete(id, version);
    }
}
