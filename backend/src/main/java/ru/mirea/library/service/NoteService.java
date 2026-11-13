package ru.mirea.library.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.mirea.library.domain.RequestNote;
import ru.mirea.library.dto.*;
import ru.mirea.library.error.*;
import ru.mirea.library.model.Role;
import ru.mirea.library.repository.NoteRepository;

import java.util.*;

@Service
@Transactional(readOnly = true)
public class NoteService {
    private final NoteRepository repository;
    private final BookRequestService requests;
    private final AuditService audit;

    public NoteService(NoteRepository repository, BookRequestService requests, AuditService audit) {
        this.repository = repository;
        this.requests = requests;
        this.audit = audit;
    }

    public List<NoteDto> list(UUID id) {
        requests.getById(id);
        return repository.findByRequestIdOrderByPinnedDescCreatedAtDesc(id).stream()
                .map(this::dto)
                .toList();
    }

    @Transactional
    public NoteDto save(UUID requestId, UUID id, NoteWrite b) {
        var request = requests.getById(requestId);
        if (request.isArchived()) throw new BusinessException("Сначала восстановите заявку");
        var u = AuditService.current();
        RequestNote n;
        if (id == null) {
            n = new RequestNote();
            n.requestId = requestId;
            n.authorId = u.id();
            n.authorLogin = u.login();
        } else {
            n = get(requestId, id);
            authorize(n);
            check(n, b.version());
        }
        n.text = b.text().trim();
        n.pinned = b.pinned();
        repository.saveAndFlush(n);
        audit.record(
                id == null ? "NOTE_CREATE" : "NOTE_UPDATE",
                "REQUEST",
                requestId,
                "Сохранена заметка");
        return dto(n);
    }

    @Transactional
    public void delete(UUID requestId, UUID id, long version) {
        var n = get(requestId, id);
        authorize(n);
        check(n, version);
        repository.delete(n);
        repository.flush();
        audit.record("NOTE_DELETE", "REQUEST", requestId, "Удалена заметка");
    }

    private RequestNote get(UUID r, UUID id) {
        return repository
                .findById(id)
                .filter(n -> n.requestId.equals(r))
                .orElseThrow(() -> new EntityNotFoundException("Заметка не найдена"));
    }

    private void authorize(RequestNote n) {
        var u = AuditService.current();
        if (!u.id().equals(n.authorId) && !u.roles().contains(Role.ADMIN))
            throw new org.springframework.security.access.AccessDeniedException(
                    "Только автор или администратор");
    }

    private void check(RequestNote n, Long v) {
        if (v == null || v != n.version)
            throw new org.springframework.orm.ObjectOptimisticLockingFailureException(
                    RequestNote.class, n.id);
    }

    private NoteDto dto(RequestNote n) {
        return new NoteDto(
                n.id,
                n.authorId,
                n.authorLogin,
                n.text,
                n.pinned,
                n.createdAt,
                n.updatedAt,
                n.version);
    }
}
