package resenkov.work.parkingreservationservice.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Component
public class JwtUtils {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public String extractUsername(String token) {
        Claims c = extractAllClaims(token);
        return c.getSubject();
    }

    @SuppressWarnings("unchecked")
    public List<String> extractRoles(String token) {
        Claims claims = extractAllClaims(token);
        Object rolesObj = claims.get("roles");
        if (rolesObj == null) return List.of();

        List<String> roles = new ArrayList<>();

        if (rolesObj instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof String s) {
                    roles.add(s);
                } else if (o instanceof Map<?,?> map) {
                    // стандартная сериализация SimpleGrantedAuthority -> {"authority":"ROLE_USER"}
                    Object authority = map.get("authority");
                    if (authority == null) {
                        // try variants
                        authority = map.get("role");
                    }
                    if (authority == null) {
                        authority = map.get("name");
                    }
                    if (authority != null) {
                        roles.add(authority.toString());
                    } else {
                        // fallback: toString
                        roles.add(map.toString());
                    }
                } else {
                    // fallback: toString
                    roles.add(o.toString());
                }
            }
            return roles;
        }

        if (rolesObj instanceof String single) {
            String[] parts = single.split("\\s*,\\s*");
            for (String p : parts) if (!p.isBlank()) roles.add(p);
            return roles;
        }

        return List.of();
    }

    public boolean isTokenExpired(String token) {
        return extractAllClaims(token).getExpiration().before(new Date());
    }
}
