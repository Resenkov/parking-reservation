package resenkov.work.parkinggateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
public class JwtValidationFilter implements GlobalFilter, Ordered {

    @Value("${jwt.secret}")
    private String secret;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // Разрешаем публичные пути без проверки
        if (path.startsWith("/auth") || path.startsWith("/user/add")) {
            return chain.filter(exchange);
        }

        List<String> authHeaders = request.getHeaders().get("Authorization");
        if (authHeaders == null || authHeaders.isEmpty()) {
            return unauthorizedResponse(exchange, "Missing Authorization header");
        }

        String authHeader = authHeaders.get(0);
        if (!authHeader.startsWith("Bearer ")) {
            return unauthorizedResponse(exchange, "Invalid Authorization format");
        }

        final String jwt = authHeader.substring(7);
        try {
            Claims claims = Jwts.parser()
                    .setSigningKey(secret.getBytes())
                    .build()
                    .parseClaimsJws(jwt)
                    .getBody();

            ServerHttpRequest modifiedRequest = request.mutate()
                    .header("X-User-Email", claims.getSubject())
                    .build();

            return chain.filter(exchange.mutate().request(modifiedRequest).build());
        } catch (Exception e) {
            return unauthorizedResponse(exchange, "Invalid JWT token");
        }
    }

    private Mono<Void> unauthorizedResponse(ServerWebExchange exchange, String reason) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().add("X-Reason", reason);
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        return -100;
    }
}