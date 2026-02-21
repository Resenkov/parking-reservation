package resenkov.work.parkingeurekaserver.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class RequestResponseLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestResponseLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long start = System.currentTimeMillis();
        String remote = request.getRemoteAddr();
        String method = request.getMethod();
        String uri = request.getRequestURI();

        log.info("Incoming request: {} {} from {}", method, uri, remote);
        try {
            filterChain.doFilter(request, response);
        } finally {
            long took = System.currentTimeMillis() - start;
            log.info("Outgoing response: {} {} -> status={} in {}ms", method, uri, response.getStatus(), took);
        }
    }
}
