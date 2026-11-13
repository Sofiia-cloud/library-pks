package ru.mirea.library;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import ru.mirea.library.security.LoginRateLimiter;

class LoginRateLimiterTest {
    @Test
    void limitsEachAddressIndependently() {
        var limiter = new LoginRateLimiter();
        for (int n = 0; n < 30; n++) limiter.check("127.0.0.1");
        assertEquals(
                429,
                assertThrows(ResponseStatusException.class, () -> limiter.check("127.0.0.1"))
                        .getStatusCode()
                        .value());
        assertDoesNotThrow(() -> limiter.check("127.0.0.2"));
    }
}
