package resenkov.work.parkinggateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class RequestResponseLoggingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RequestResponseLoggingFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long start = System.currentTimeMillis();
        String method = exchange.getRequest().getMethod() != null
                ? exchange.getRequest().getMethod().name() : "UNKNOWN";
        String path = exchange.getRequest().getURI().getRawPath();
        String remote = exchange.getRequest().getRemoteAddress() != null
                ? String.valueOf(exchange.getRequest().getRemoteAddress().getAddress()) : "unknown";

        log.info("Incoming request: {} {} from {}", method, path, remote);

        return chain.filter(exchange)
                .doFinally(signalType -> {
                    long took = System.currentTimeMillis() - start;
                    int status = exchange.getResponse().getStatusCode() != null
                            ? exchange.getResponse().getStatusCode().value() : 0;
                    log.info("Outgoing response: {} {} -> status={} in {}ms", method, path, status, took);
                });
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
