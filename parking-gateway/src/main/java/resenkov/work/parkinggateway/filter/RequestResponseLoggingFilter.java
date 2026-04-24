package resenkov.work.parkinggateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.http.server.reactive.ServerHttpRequest;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
public class RequestResponseLoggingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RequestResponseLoggingFilter.class);
    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long start = System.currentTimeMillis();
        String method = exchange.getRequest().getMethod() != null
                ? exchange.getRequest().getMethod().name() : "UNKNOWN";
        String path = exchange.getRequest().getURI().getRawPath();
        String remote = exchange.getRequest().getRemoteAddress() != null
                ? String.valueOf(exchange.getRequest().getRemoteAddress().getAddress()) : "unknown";
        String requestId = exchange.getRequest().getHeaders().getFirst(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header(REQUEST_ID_HEADER, requestId)
                .build();
        ServerWebExchange mutatedExchange = exchange.mutate().request(mutatedRequest).build();
        exchange.getResponse().getHeaders().set(REQUEST_ID_HEADER, requestId);

        log.info(
                "Получен запрос на gateway: метод={}, uri={}, ip={}, requestId={}",
                method,
                path,
                remote,
                requestId
        );

        String finalRequestId = requestId;
        return chain.filter(mutatedExchange)
                .doFinally(signalType -> {
                    long took = System.currentTimeMillis() - start;
                    int status = exchange.getResponse().getStatusCode() != null
                            ? exchange.getResponse().getStatusCode().value() : 0;
                    log.info(
                            "Gateway вернул ответ: метод={}, uri={}, статус={}, длительность={}мс, requestId={}",
                            method,
                            path,
                            status,
                            took,
                            finalRequestId
                    );
                });
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
