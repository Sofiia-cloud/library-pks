package ru.mirea.library.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;

import org.springframework.http.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import ru.mirea.library.dto.*;
import ru.mirea.library.filter.RequestFilter;
import ru.mirea.library.service.*;

import java.net.URI;
import java.util.*;

@RestController
@RequestMapping("/api/book-requests")
@Validated
public class BookRequestController {
    private final BookRequestService service;
    private final RequestQueryService query;
    private final NoteService notes;
    private final BulkService bulk;
    private final CsvService csv;

    public BookRequestController(
            BookRequestService service,
            RequestQueryService query,
            NoteService notes,
            BulkService bulk,
            CsvService csv) {
        this.service = service;
        this.query = query;
        this.notes = notes;
        this.bulk = bulk;
        this.csv = csv;
    }

    @GetMapping
    public PageDto<BookRequestDto> list(
            @ModelAttribute RequestFilter f,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "25") int size) {
        return query.list(f, page, size);
    }

    @GetMapping("/{id}")
    public BookRequestDto get(@PathVariable("id") UUID id) {
        return DtoMapper.request(service.getById(id));
    }

    @PostMapping
    public ResponseEntity<BookRequestDto> create(@Valid @RequestBody BookRequestCreate b) {
        var r = service.create(b);
        return ResponseEntity.created(URI.create("/api/book-requests/" + r.getId()))
                .body(DtoMapper.request(r));
    }

    @PutMapping("/{id}")
    public BookRequestDto update(
            @PathVariable("id") UUID id, @Valid @RequestBody BookRequestUpdate b) {
        return DtoMapper.request(service.update(id, b));
    }

    @PutMapping("/{id}/status")
    public BookRequestDto status(@PathVariable("id") UUID id, @Valid @RequestBody StatusUpdate b) {
        return DtoMapper.request(service.changeStatus(id, b.version(), b.status()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(
            @PathVariable("id") UUID id, @RequestParam("version") @PositiveOrZero long version) {
        service.delete(id, version);
    }

    @PostMapping("/{id}/restore")
    public BookRequestDto restore(
            @PathVariable("id") UUID id, @Valid @RequestBody VersionRequest b) {
        return DtoMapper.request(service.restore(id, b.version()));
    }

    @DeleteMapping("/{id}/permanent")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void permanent(
            @PathVariable("id") UUID id, @RequestParam("version") @PositiveOrZero long v) {
        service.permanent(id, v);
    }

    @PostMapping("/bulk")
    public BulkResult bulk(@Valid @RequestBody BulkRequest b) {
        return bulk.apply(b);
    }

    @GetMapping("/{id}/notes")
    public List<NoteDto> notes(@PathVariable("id") UUID id) {
        return notes.list(id);
    }

    @PostMapping("/{id}/notes")
    public NoteDto note(@PathVariable("id") UUID id, @Valid @RequestBody NoteWrite b) {
        return notes.save(id, null, b);
    }

    @PutMapping("/{id}/notes/{noteId}")
    public NoteDto note(
            @PathVariable("id") UUID id,
            @PathVariable("noteId") UUID noteId,
            @Valid @RequestBody NoteWrite b) {
        return notes.save(id, noteId, b);
    }

    @DeleteMapping("/{id}/notes/{noteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeNote(
            @PathVariable("id") UUID id,
            @PathVariable("noteId") UUID noteId,
            @RequestParam("version") @PositiveOrZero long v) {
        notes.delete(id, noteId, v);
    }

    @PostMapping("/import/preview")
    public CsvPreview preview(@Valid @RequestBody CsvPreviewRequest b) {
        return csv.preview(b);
    }

    @PostMapping("/import/apply")
    public CsvResult apply(@Valid @RequestBody CsvApply b) {
        return csv.apply(b.previewId());
    }

    @GetMapping(value = "/export", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<String> export(@ModelAttribute RequestFilter f) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=book-requests.csv")
                .body(csv.export(f));
    }
}
