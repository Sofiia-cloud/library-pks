package ru.mirea.library.service;

import org.springframework.data.domain.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.mirea.library.domain.AuditEvent;
import ru.mirea.library.dto.*;
import ru.mirea.library.repository.AuditRepository;
import ru.mirea.library.security.CurrentUser;

import java.util.*;

@Service
public class AuditService {
    private final AuditRepository repository;

    public AuditService(AuditRepository repository) {
        this.repository = repository;
    }

    public static CurrentUser current() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof CurrentUser u ? u : null;
    }

    @Transactional
    public void record(String action, String type, UUID id, String description) {
        var u = current();
        recordAs(u == null ? "system" : u.login(), action, type, id, description);
    }

    @Transactional
    public void recordAs(String actor, String action, String type, UUID id, String description) {
        String cid = org.slf4j.MDC.get("correlationId");
        repository.save(
                new AuditEvent(
                        actor,
                        action,
                        type,
                        id,
                        description,
                        cid == null ? UUID.randomUUID().toString() : cid));
    }

    @Transactional(readOnly = true)
    public PageDto<AuditDto> list(String text, int page, int size, String sort, boolean ascending) {
        Paging.check(page, size);
        if (!Set.of("occurredAt", "action", "actorLogin", "entityType").contains(sort))
            throw new ru.mirea.library.error.BusinessException("Неизвестное поле сортировки");
        org.springframework.data.jpa.domain.Specification<AuditEvent> spec =
                (r, q, c) -> {
                    if (text == null || text.isBlank()) return c.conjunction();
                    String p =
                            "%"
                                    + text.toLowerCase(Locale.ROOT)
                                            .replace("!", "!!")
                                            .replace("%", "!%")
                                            .replace("_", "!_")
                                    + "%";
                    return c.or(
                            c.like(c.lower(r.get("actorLogin")), p, '!'),
                            c.like(c.lower(r.get("description")), p, '!'),
                            c.like(c.lower(r.get("action")), p, '!'),
                            c.like(c.lower(r.get("correlationId")), p, '!'));
                };
        var result =
                repository.findAll(
                        spec,
                        PageRequest.of(
                                page,
                                size,
                                Sort.by(ascending ? Sort.Direction.ASC : Sort.Direction.DESC, sort)
                                        .and(Sort.by("id"))));
        return Paging.dto(
                result.map(
                        e ->
                                new AuditDto(
                                        e.getId(),
                                        e.getActorLogin(),
                                        e.getAction(),
                                        e.getEntityType(),
                                        e.getEntityId(),
                                        e.getOccurredAt(),
                                        e.getDescription(),
                                        e.getCorrelationId())));
    }
}
