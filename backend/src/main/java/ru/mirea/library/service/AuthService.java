package ru.mirea.library.service;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.mirea.library.domain.*;
import ru.mirea.library.dto.*;
import ru.mirea.library.error.BusinessException;
import ru.mirea.library.repository.*;
import ru.mirea.library.security.*;

import java.time.Instant;
import java.util.Locale;

@Service
public class AuthService {
    private final UserAccountRepository users;
    private final RefreshSessionRepository sessions;
    private final PasswordEncoder passwords;
    private final PasswordPolicy policy;
    private final TokenService tokens;
    private final String dummyHash;

    public AuthService(
            UserAccountRepository users,
            RefreshSessionRepository sessions,
            PasswordEncoder passwords,
            PasswordPolicy policy,
            TokenService tokens) {
        this.users = users;
        this.sessions = sessions;
        this.passwords = passwords;
        this.policy = policy;
        this.tokens = tokens;
        dummyHash = passwords.encode("dummy-account-password");
    }

    @Transactional(noRollbackFor = BadCredentialsException.class)
    public TokenResponse login(LoginRequest request) {
        if (request.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
            throw denied();
        String login = request.login().trim().toLowerCase(Locale.ROOT);
        UserAccount user = users.lockByLogin(login).orElse(null);
        if (user == null) {
            passwords.matches(request.password(), dummyHash);
            throw denied();
        }
        Instant now = Instant.now();
        if (!user.isActive()
                || (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)))
            throw denied();
        if (user.getLockedUntil() != null) {
            user.setLockedUntil(null);
            user.setFailedAttempts(0);
        }
        if (!passwords.matches(request.password(), user.getPasswordHash())) {
            user.setFailedAttempts(user.getFailedAttempts() + 1);
            if (user.getFailedAttempts() >= 5) user.setLockedUntil(now.plusSeconds(900));
            users.saveAndFlush(user);
            throw denied();
        }
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        users.saveAndFlush(user);
        String refresh = tokens.refresh();
        RefreshSession session =
                sessions.saveAndFlush(
                        new RefreshSession(
                                user, tokens.hash(refresh), now, tokens.refreshExpiry()));
        return response(session, refresh);
    }

    @Transactional
    public TokenResponse refresh(RefreshRequest request) {
        RefreshSession session =
                sessions.lockByTokenHash(tokens.hash(request.refreshToken()))
                        .orElseThrow(AuthService::denied);
        if (session.isRevoked()
                || !session.getExpiresAt().isAfter(Instant.now())
                || !session.getUser().isActive()) throw denied();
        String refresh = tokens.refresh();
        session.setTokenHash(tokens.hash(refresh));
        sessions.saveAndFlush(session);
        return response(session, refresh);
    }

    @Transactional
    public void logout(RefreshRequest request) {
        sessions.lockByTokenHash(tokens.hash(request.refreshToken()))
                .ifPresent(
                        session -> {
                            session.setRevoked(true);
                            sessions.saveAndFlush(session);
                        });
    }

    @Transactional(readOnly = true)
    public UserDto me(CurrentUser current) {
        return userDto(
                users.findById(current.id())
                        .filter(UserAccount::isActive)
                        .orElseThrow(AuthService::denied));
    }

    @Transactional
    public void changePassword(CurrentUser current, PasswordChangeRequest request) {
        UserAccount user =
                users.lockById(current.id())
                        .filter(UserAccount::isActive)
                        .orElseThrow(AuthService::denied);
        if (!passwords.matches(request.currentPassword(), user.getPasswordHash()))
            throw new BusinessException("Текущий пароль неверен");
        policy.validate(request.newPassword());
        if (passwords.matches(request.newPassword(), user.getPasswordHash()))
            throw new BusinessException("Новый пароль должен отличаться от текущего");
        user.setPasswordHash(passwords.encode(request.newPassword()));
        user.setMustChangePassword(false);
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        users.saveAndFlush(user);
        sessions.revokeForUser(user.getId());
    }

    private TokenResponse response(RefreshSession session, String refresh) {
        Instant expires = tokens.accessExpiry();
        return new TokenResponse(
                tokens.access(session, expires), refresh, expires, userDto(session.getUser()));
    }

    public static UserDto userDto(UserAccount user) {
        return new UserDto(
                user.getId(),
                user.getLogin(),
                user.getName(),
                user.getRoles(),
                user.isMustChangePassword());
    }

    private static BadCredentialsException denied() {
        return new BadCredentialsException("Неверные учётные данные или сессия недействительна");
    }
}
