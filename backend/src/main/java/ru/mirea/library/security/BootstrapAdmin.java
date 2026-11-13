package ru.mirea.library.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import ru.mirea.library.domain.UserAccount;
import ru.mirea.library.model.Role;
import ru.mirea.library.repository.UserAccountRepository;

import java.util.*;

@Component
public class BootstrapAdmin implements ApplicationRunner {
    private final UserAccountRepository users;
    private final PasswordEncoder encoder;
    private final PasswordPolicy policy;
    private final String login, name, password;

    public BootstrapAdmin(
            UserAccountRepository users,
            PasswordEncoder encoder,
            PasswordPolicy policy,
            @Value("${app.bootstrap.login:}") String login,
            @Value("${app.bootstrap.name:Администратор}") String name,
            @Value("${app.bootstrap.password:}") String password) {
        this.users = users;
        this.encoder = encoder;
        this.policy = policy;
        this.login = login;
        this.name = name;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (login.isBlank()) return;
        String normalized = login.trim().toLowerCase(Locale.ROOT);
        if (users.findByLoginIgnoreCase(normalized).isPresent()) return;
        if (normalized.length() > 128 || name.isBlank() || name.length() > 150)
            throw new IllegalStateException("Некорректные настройки первого администратора");
        policy.validate(password);
        users.saveAndFlush(
                new UserAccount(
                        normalized, name.trim(), encoder.encode(password), Set.of(Role.ADMIN)));
    }
}
