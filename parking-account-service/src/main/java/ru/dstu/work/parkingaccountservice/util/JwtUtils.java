package ru.dstu.work.parkingaccountservice.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.dstu.work.parkingaccountservice.exception.BadRequestException;

import javax.crypto.SecretKey;

@Component
public class JwtUtils {

    @Value("${jwt.secret:c2VjdXJpdHlLZXlGb3JBdXRoZW50aWNhdGlvbkFuZEF1dGhvcml6YXRpb24K}")
    private String secret;

    public String extractUsernameFromAuthorizationHeader(String authorizationHeader) {
        return extractUsername(extractBearerToken(authorizationHeader));
    }

    public Long extractUserIdFromAuthorizationHeader(String authorizationHeader) {
        return extractUserId(extractBearerToken(authorizationHeader));
    }

    public String extractUsername(String token) {
        return extractAllClaims(token).getSubject();
    }

    public Long extractUserId(String token) {
        Object value = extractAllClaims(token).get("userId");
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Claims extractAllClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException ex) {
            throw new BadRequestException("Некорректный токен авторизации");
        }
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }

    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw new BadRequestException("Authorization header is required");
        }
        if (!authorizationHeader.startsWith("Bearer ")) {
            throw new BadRequestException("Authorization header must use Bearer token");
        }
        String token = authorizationHeader.substring(7).trim();
        if (token.isBlank()) {
            throw new BadRequestException("Bearer token is empty");
        }
        return token;
    }
}
