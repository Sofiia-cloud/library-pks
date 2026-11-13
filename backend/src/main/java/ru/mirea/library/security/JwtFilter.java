package ru.mirea.library.security;

import io.jsonwebtoken.*;

import jakarta.servlet.*;
import jakarta.servlet.http.*;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import ru.mirea.library.domain.RefreshSession;
import ru.mirea.library.domain.UserAccount;
import ru.mirea.library.model.Role;
import ru.mirea.library.repository.RefreshSessionRepository;

import java.io.IOException;
import java.time.Instant;
import java.util.*;

@Component
public class JwtFilter extends OncePerRequestFilter {
    private final TokenService tokens;
    private final RefreshSessionRepository sessions;
    private final ErrorWriter errors;

    public JwtFilter(TokenService tokens, RefreshSessionRepository sessions, ErrorWriter errors) {
        this.tokens = tokens;
        this.sessions = sessions;
        this.errors = errors;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null) {
            try {
                if (!header.startsWith("Bearer ")) throw new IllegalArgumentException();
                Claims claims = tokens.parse(header.substring(7));
                RefreshSession session =
                        sessions.findById(UUID.fromString(claims.get("sid", String.class)))
                                .orElseThrow(IllegalArgumentException::new);
                UserAccount user = session.getUser();
                if (session.isRevoked()
                        || !session.getExpiresAt().isAfter(Instant.now())
                        || !user.isActive()
                        || !user.getId().toString().equals(claims.getSubject()))
                    throw new IllegalArgumentException();
                Set<Role> roles = EnumSet.noneOf(Role.class);
                roles.addAll(user.getRoles());
                if (roles.contains(Role.ADMIN)) roles.add(Role.LIBRARIAN);
                if (roles.contains(Role.LIBRARIAN)) roles.add(Role.VIEWER);
                CurrentUser principal =
                        new CurrentUser(
                                user.getId(),
                                user.getLogin(),
                                user.getRoles(),
                                user.isMustChangePassword());
                var authorities =
                        roles.stream()
                                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                                .toList();
                SecurityContextHolder.getContext()
                        .setAuthentication(
                                new UsernamePasswordAuthenticationToken(
                                        principal, null, authorities));
                String uri = request.getRequestURI();
                if (user.isMustChangePassword()
                        && !Set.of(
                                        "/api/auth/me",
                                        "/api/auth/change-password",
                                        "/api/auth/logout",
                                        "/api/auth/refresh")
                                .contains(uri)) {
                    errors.write(
                            response,
                            403,
                            "PASSWORD_CHANGE_REQUIRED",
                            "Сначала смените временный пароль");
                    return;
                }
            } catch (JwtException | IllegalArgumentException e) {
                SecurityContextHolder.clearContext();
                errors.write(
                        response,
                        401,
                        "UNAUTHORIZED",
                        "Сессия истекла или недействительна. Войдите снова");
                return;
            } catch (org.springframework.dao.DataAccessException e) {
                SecurityContextHolder.clearContext();
                errors.write(response, 500, "INTERNAL_ERROR", "Не удалось проверить сессию");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
