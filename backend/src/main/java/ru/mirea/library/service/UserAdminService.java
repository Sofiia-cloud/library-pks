package ru.mirea.library.service;

import org.springframework.data.domain.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.mirea.library.domain.*;
import ru.mirea.library.dto.*;
import ru.mirea.library.error.*;
import ru.mirea.library.model.*;
import ru.mirea.library.repository.*;
import ru.mirea.library.security.PasswordPolicy;

import java.util.*;

@Service
@Transactional(readOnly = true)
public class UserAdminService {
    private final UserAccountRepository users;
    private final RefreshSessionRepository sessions;
    private final PasswordEncoder passwords;
    private final PasswordPolicy policy;
    private final AuditService audit;

    public UserAdminService(
            UserAccountRepository users,
            RefreshSessionRepository sessions,
            PasswordEncoder passwords,
            PasswordPolicy policy,
            AuditService audit) {
        this.users = users;
        this.sessions = sessions;
        this.passwords = passwords;
        this.policy = policy;
        this.audit = audit;
    }

    public PageDto<AdminUserDto> list(String text, int page, int size) {
        Paging.check(page, size);
        org.springframework.data.jpa.domain.Specification<UserAccount> spec =
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
                            c.like(c.lower(r.get("login")), p, '!'),
                            c.like(c.lower(r.get("name")), p, '!'));
                };
        return Paging.dto(
                users.findAll(spec, PageRequest.of(page, size, Sort.by("login").and(Sort.by("id"))))
                        .map(this::dto));
    }

    @Transactional
    public AdminUserDto create(AdminUserCreate b) {
        String login = login(b.login());
        unique(login, null);
        policy.validate(b.temporaryPassword());
        UserAccount u =
                new UserAccount(
                        login, b.name().trim(), passwords.encode(b.temporaryPassword()), b.roles());
        users.saveAndFlush(u);
        audit.record("USER_CREATE", "USER", u.getId(), "Создана учётная запись");
        return dto(u);
    }

    @Transactional
    public AdminUserDto update(UUID id, AdminUserUpdate b) {
        var admins = users.lockAdministrators(Role.ADMIN);
        var u = get(id);
        version(u, b.version());
        var current = AuditService.current();
        if (current.id().equals(id) && (!b.active() || !b.roles().contains(Role.ADMIN)))
            throw new BusinessException(
                    "Нельзя отключить себя или снять у себя роль администратора");
        if (u.isActive()
                && u.getRoles().contains(Role.ADMIN)
                && (!b.active() || !b.roles().contains(Role.ADMIN))
                && admins.size() <= 1)
            throw new BusinessException("Нужен хотя бы один активный администратор");
        String login = login(b.login());
        unique(login, id);
        u.setLogin(login);
        u.setName(b.name().trim());
        u.setRoles(b.roles());
        u.setActive(b.active());
        users.saveAndFlush(u);
        sessions.revokeForUser(id);
        audit.record("USER_UPDATE", "USER", id, "Изменена учётная запись; сессии отозваны");
        return dto(u);
    }

    @Transactional
    public AdminUserDto reset(UUID id, PasswordReset b) {
        users.lockAdministrators(Role.ADMIN);
        UserAccount u = get(id);
        version(u, b.version());
        if (AuditService.current().id().equals(id))
            throw new BusinessException("Свой пароль меняется в разделе смены пароля");
        policy.validate(b.temporaryPassword());
        u.setPasswordHash(passwords.encode(b.temporaryPassword()));
        u.setMustChangePassword(true);
        u.setFailedAttempts(0);
        u.setLockedUntil(null);
        users.saveAndFlush(u);
        sessions.revokeForUser(id);
        audit.record("PASSWORD_RESET", "USER", id, "Установлен временный пароль; сессии отозваны");
        return dto(u);
    }

    private UserAccount get(UUID id) {
        return users.lockById(id)
                .orElseThrow(() -> new EntityNotFoundException("Пользователь не найден"));
    }

    private void version(UserAccount u, long v) {
        if (u.getVersion() != v)
            throw new org.springframework.orm.ObjectOptimisticLockingFailureException(
                    UserAccount.class, u.getId());
    }

    private String login(String s) {
        return s.trim().toLowerCase(Locale.ROOT);
    }

    private void unique(String login, UUID id) {
        if (users.findByLoginIgnoreCase(login).filter(u -> !u.getId().equals(id)).isPresent())
            throw new BusinessException("Логин уже занят");
    }

    private AdminUserDto dto(UserAccount u) {
        return new AdminUserDto(
                u.getId(),
                u.getLogin(),
                u.getName(),
                u.getRoles(),
                u.isActive(),
                u.isMustChangePassword(),
                u.getCreatedAt(),
                u.getVersion());
    }
}
