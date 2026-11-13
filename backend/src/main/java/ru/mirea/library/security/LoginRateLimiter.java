package ru.mirea.library.security;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;

@Component
public class LoginRateLimiter {
    private final Map<String, Deque<Instant>> attempts = new LinkedHashMap<>();

    public synchronized void check(String address) {
        Instant cutoff = Instant.now().minusSeconds(60);
        attempts.entrySet()
                .removeIf(e -> e.getValue().isEmpty() || e.getValue().peekLast().isBefore(cutoff));
        if (!attempts.containsKey(address) && attempts.size() >= 1024)
            attempts.remove(attempts.keySet().iterator().next());
        Deque<Instant> times = attempts.computeIfAbsent(address, key -> new ArrayDeque<>());
        while (!times.isEmpty() && times.peekFirst().isBefore(cutoff)) times.removeFirst();
        if (times.size() >= 30)
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS, "Слишком много попыток входа. Подождите минуту");
        times.addLast(Instant.now());
    }
}
