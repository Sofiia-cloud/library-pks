package ru.mirea.library.security;

import org.springframework.stereotype.Component;

import ru.mirea.library.error.BusinessException;

import java.nio.charset.StandardCharsets;

@Component
public class PasswordPolicy {
    public void validate(String password) {
        if (password == null
                || password.isBlank()
                || password.length() < 10
                || password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new BusinessException(
                    "Пароль должен содержать не менее 10 символов и занимать не более 72 байт"
                        + " UTF-8");
    }
}
