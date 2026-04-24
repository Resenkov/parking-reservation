package resenkov.work.parkingreservationservice.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

@Component
public class RequestResponseLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestResponseLoggingFilter.class);
    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String REQUEST_ID_MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long start = System.currentTimeMillis();
        String remote = request.getRemoteAddr();
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String requestId = Optional.ofNullable(request.getHeader(REQUEST_ID_HEADER))
                .filter(value -> !value.isBlank())
                .orElse(UUID.randomUUID().toString());

        MDC.put(REQUEST_ID_MDC_KEY, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        log.info("Получен HTTP-запрос: метод={}, uri={}, ip={}, requestId={}", method, uri, remote, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            long took = System.currentTimeMillis() - start;
            log.info(
                    "Сформирован HTTP-ответ: метод={}, uri={}, статус={}, длительность={}мс, requestId={}",
                    method,
                    uri,
                    response.getStatus(),
                    took,
                    requestId
            );
            MDC.remove(REQUEST_ID_MDC_KEY);
        }
    }
}
