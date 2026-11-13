package ru.mirea.library.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import org.springframework.stereotype.Component;

import ru.mirea.library.config.AuthProperties;
import ru.mirea.library.domain.RefreshSession;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;

import javax.crypto.SecretKey;

@Component
public class TokenService {
    private final AuthProperties properties;
    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public TokenService(AuthProperties properties) {
        this.properties = properties;
        byte[] bytes = Decoders.BASE64.decode(properties.secretBase64());
        if (bytes.length < 32)
            throw new IllegalStateException("JWT secret must contain at least 32 random bytes");
        key = Keys.hmacShaKeyFor(bytes);
    }

    public Instant accessExpiry() {
        return Instant.now().plusSeconds(properties.accessMinutes() * 60L);
    }

    public Instant refreshExpiry() {
        return Instant.now().plusSeconds(properties.refreshDays() * 86400L);
    }

    public String access(RefreshSession session, Instant expires) {
        return Jwts.builder()
                .issuer(properties.issuer())
                .subject(session.getUser().getId().toString())
                .claim("sid", session.getId().toString())
                .issuedAt(new Date())
                .expiration(Date.from(expires))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public Claims parse(String jwt) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(properties.issuer())
                .build()
                .parseSignedClaims(jwt)
                .getPayload();
    }

    public String refresh() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hash(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
