package ru.mirea.library.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;

import org.springframework.http.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import ru.mirea.library.dto.*;
import ru.mirea.library.service.ReaderService;

import java.net.URI;
import java.util.*;

@RestController
@RequestMapping("/api/readers")
@Validated
public class ReaderController {
    private final ReaderService service;

    public ReaderController(ReaderService service) {
        this.service = service;
    }

    @GetMapping
    public List<ReaderDto> list() {
        return service.getAll().stream().map(DtoMapper::reader).toList();
    }

    @GetMapping("/{id}")
    public ReaderDto get(@PathVariable("id") UUID id) {
        return DtoMapper.reader(service.getById(id));
    }

    @PostMapping
    public ResponseEntity<ReaderDto> create(@Valid @RequestBody ReaderCreate body) {
        var reader =
                service.create(
                        body.fullName(), body.email(), body.phone(), body.libraryCardNumber());
        return ResponseEntity.created(URI.create("/api/readers/" + reader.getId()))
                .body(DtoMapper.reader(reader));
    }

    @PutMapping("/{id}")
    public ReaderDto update(@PathVariable("id") UUID id, @Valid @RequestBody ReaderUpdate body) {
        return DtoMapper.reader(
                service.update(
                        id,
                        body.version(),
                        body.fullName(),
                        body.email(),
                        body.phone(),
                        body.libraryCardNumber()));
    }

    @PutMapping("/{id}/active")
    public ReaderDto active(@PathVariable("id") UUID id, @Valid @RequestBody ActiveUpdate body) {
        return DtoMapper.reader(service.setActive(id, body.version(), body.active()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable("id") UUID id, @RequestParam("version") @PositiveOrZero long version) {
        service.delete(id, version);
    }
}
