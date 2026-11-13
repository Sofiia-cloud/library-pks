package ru.mirea.library.controller;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.*;

import ru.mirea.library.dto.*;
import ru.mirea.library.service.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final UserAdminService users;
    private final AuditService audit;

    public AdminController(UserAdminService users, AuditService audit) {
        this.users = users;
        this.audit = audit;
    }

    @GetMapping("/users")
    public PageDto<AdminUserDto> users(
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "25") int size) {
        return users.list(q, page, size);
    }

    @PostMapping("/users")
    public AdminUserDto create(@Valid @RequestBody AdminUserCreate b) {
        return users.create(b);
    }

    @PutMapping("/users/{id}")
    public AdminUserDto update(@PathVariable("id") UUID id, @Valid @RequestBody AdminUserUpdate b) {
        return users.update(id, b);
    }

    @PostMapping("/users/{id}/reset-password")
    public AdminUserDto reset(@PathVariable("id") UUID id, @Valid @RequestBody PasswordReset b) {
        return users.reset(id, b);
    }

    @GetMapping("/audit")
    public PageDto<AuditDto> audit(
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "25") int size,
            @RequestParam(value = "sort", defaultValue = "occurredAt") String sort,
            @RequestParam(value = "ascending", defaultValue = "false") boolean asc) {
        return audit.list(q, page, size, sort, asc);
    }
}
